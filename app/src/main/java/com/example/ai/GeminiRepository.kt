package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.ChatMessageEntity
import com.example.data.CoreSettings
import com.example.data.MemoryEntity
import com.example.data.MissionEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

private const val TAG = "GeminiRepository"

data class JarvisResponseResult(
    val replyText: String,
    val toolLogs: List<String>,
    val modelLabel: String
)

data class ExtractedMemory(
    val category: String,
    val fact: String
)

interface JarvisToolExecutor {
    suspend fun manageMissions(
        action: String,
        title: String?,
        priority: String?,
        dueTime: String?
    ): Map<String, JsonPrimitive>

    suspend fun saveNeuralMemory(
        category: String,
        fact: String
    ): Map<String, JsonPrimitive>

    suspend fun getDeviceTelemetry(): Map<String, JsonPrimitive>

    suspend fun setTacticalTimer(
        seconds: Int,
        label: String
    ): Map<String, JsonPrimitive>
}

class GeminiRepository {

    private val jarvisToolDeclarations: Tool by lazy {
        val manageMissionsTool = FunctionDeclaration(
            name = "manage_missions",
            description = "Add, complete, list, or delete missions/tasks in the user's local Room tactical mission database.",
            parameters = mapOf(
                "action" to Schema.enumeration(
                    values = listOf("add", "complete", "list", "delete"),
                    description = "The mission operation: add, complete, list, or delete."
                ),
                "title" to Schema.string(
                    description = "Title or short description of the mission (required for add, complete, delete)."
                ),
                "priority" to Schema.enumeration(
                    values = listOf("CRITICAL", "HIGH", "NORMAL", "LOW"),
                    description = "Priority level for a newly added mission."
                ),
                "due_time" to Schema.string(
                    description = "Optional deadline or scheduled time (e.g., '18:00', 'Tomorrow 09:00')."
                )
            ),
            optionalParameters = listOf("title", "priority", "due_time")
        )

        val saveNeuralMemoryTool = FunctionDeclaration(
            name = "save_neural_memory",
            description = "Store an important long-term fact, preference, or personal profile detail about the user in the Neural Memory Vault.",
            parameters = mapOf(
                "category" to Schema.enumeration(
                    values = listOf("PROFILE", "PREFERENCE", "WORK", "HEALTH", "PERSONAL", "INTEL"),
                    description = "Category under which to archive this memory."
                ),
                "fact" to Schema.string(
                    description = "Clear, concise factual statement to remember about the user."
                )
            )
        )

        val getDeviceTelemetryTool = FunctionDeclaration(
            name = "get_device_telemetry",
            description = "Read live Android hardware metrics including battery percentage, charging status, Wi-Fi/Cellular connectivity, device model, and local time.",
            parameters = emptyMap()
        )

        val setTacticalTimerTool = FunctionDeclaration(
            name = "set_tactical_timer",
            description = "Start a live HUD countdown timer with vibration and acoustic alert when it finishes.",
            parameters = mapOf(
                "seconds" to Schema.integer(
                    description = "Countdown duration in seconds (positive integer, e.g., 60 for 1 minute)."
                ),
                "label" to Schema.string(
                    description = "Short tactical label for the countdown timer."
                )
            )
        )

        Tool.functionDeclarations(
            listOf(
                manageMissionsTool,
                saveNeuralMemoryTool,
                getDeviceTelemetryTool,
                setTacticalTimerTool
            )
        )
    }

    private val memoryExtractionModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "shouldSave" to Schema.boolean(
                    description = "True only if the user explicitly shared a durable personal fact, preference, identity detail, or project context worth remembering long-term."
                ),
                "category" to Schema.enumeration(
                    values = listOf("PROFILE", "PREFERENCE", "WORK", "HEALTH", "PERSONAL", "INTEL"),
                    description = "Memory category"
                ),
                "fact" to Schema.string(
                    description = "Concise third-person or factual memory entry (e.g. 'User prefers dark roast coffee')."
                )
            ),
            optionalProperties = listOf("category", "fact")
        )
        Firebase.ai.generativeModel(
            modelName = CoreSettings.MODEL_FAST_TACTICAL,
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    suspend fun sendCommand(
        userPrompt: String,
        attachedImage: Bitmap?,
        recentHistory: List<ChatMessageEntity>,
        relevantMemories: List<MemoryEntity>,
        pendingMissions: List<MissionEntity>,
        settings: CoreSettings,
        toolExecutor: JarvisToolExecutor,
        onProtocolExecuting: (String?) -> Unit
    ): JarvisResponseResult {
        val systemPrompt = buildSystemInstruction(
            commanderTitle = settings.commanderTitle,
            memories = relevantMemories,
            pendingMissions = pendingMissions
        )

        val model = Firebase.ai.generativeModel(
            modelName = settings.activeModelId,
            systemInstruction = content { text(systemPrompt) },
            tools = listOf(jarvisToolDeclarations),
            generationConfig = generationConfig {
                temperature = if (settings.overclockEnabled) 0.5f else 0.7f
            }
        )

        val historyContents: List<Content> = recentHistory
            .filter { it.role == "USER" || it.role == "JARVIS" }
            .takeLast(10)
            .map { msg ->
                val role = if (msg.role == "USER") "user" else "model"
                content(role) { text(msg.content) }
            }

        val chat = model.startChat(history = historyContents)
        val executedLogs = mutableListOf<String>()

        val userContent = content("user") {
            if (attachedImage != null) {
                image(scaleBitmapForVision(attachedImage))
            }
            val effectivePrompt = userPrompt.ifBlank {
                "Analyze this visual feed and provide a tactical assessment."
            }
            text(effectivePrompt)
        }

        try {
            var response = withTimeout(60_000L) {
                chat.sendMessage(userContent)
            }

            var loopGuard = 0
            while (response.functionCalls.isNotEmpty() && loopGuard < 5) {
                loopGuard++
                val calls = response.functionCalls
                val responseParts = mutableListOf<FunctionResponsePart>()

                for (call in calls) {
                    val protocolLabel = formatProtocolBanner(call)
                    onProtocolExecuting(protocolLabel)
                    val resultMap = dispatchToolCall(call, toolExecutor)
                    executedLogs.add(protocolLabel)
                    responseParts.add(
                        FunctionResponsePart(
                            name = call.name,
                            response = JsonObject(resultMap)
                        )
                    )
                }

                onProtocolExecuting("SYNTHESIZING TELEMETRY")
                val functionTurn = content("function") {
                    responseParts.forEach { part(it) }
                }
                response = withTimeout(60_000L) {
                    chat.sendMessage(functionTurn)
                }
            }

            onProtocolExecuting(null)
            val reply = response.text?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Protocol executed, ${settings.commanderTitle}. All systems nominal."

            return JarvisResponseResult(
                replyText = reply,
                toolLogs = executedLogs,
                modelLabel = settings.activeModelLabel
            )
        } catch (e: TimeoutCancellationException) {
            onProtocolExecuting(null)
            Log.e(TAG, "Gemini command timed out", e)
            throw IllegalStateException("Neural link timed out after 60 seconds. Please retry, ${settings.commanderTitle}.")
        } catch (e: CancellationException) {
            onProtocolExecuting(null)
            throw e
        } catch (e: Exception) {
            onProtocolExecuting(null)
            Log.e(TAG, "Gemini AI inference failed", e)
            throw e
        }
    }

    suspend fun extractMemoryIfWorthy(userPrompt: String): ExtractedMemory? {
        if (userPrompt.trim().length < 14) return null
        return try {
            val extractionPrompt = """
                Analyze this single user message and determine if it reveals a durable personal fact, name, role, location, goal, or preference worth saving in a personal assistant's memory vault. Ignore temporary commands, questions, timers, or task requests.
                User message: "$userPrompt"
            """.trimIndent()
            val response = withTimeout(30_000L) {
                memoryExtractionModel.generateContent(extractionPrompt)
            }
            val raw = response.text ?: return null
            val json = JSONObject(raw)
            if (!json.optBoolean("shouldSave", false)) return null
            val fact = json.optString("fact", "").trim()
            val category = json.optString("category", "INTEL").trim()
            if (fact.length >= 6) ExtractedMemory(category, fact) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background memory extraction skipped", e)
            null
        }
    }

    private suspend fun dispatchToolCall(
        call: FunctionCallPart,
        executor: JarvisToolExecutor
    ): Map<String, JsonPrimitive> {
        return when (call.name) {
            "manage_missions" -> {
                val action = call.args["action"]?.jsonPrimitive?.contentOrNull ?: "list"
                val title = call.args["title"]?.jsonPrimitive?.contentOrNull
                val priority = call.args["priority"]?.jsonPrimitive?.contentOrNull
                val dueTime = call.args["due_time"]?.jsonPrimitive?.contentOrNull
                executor.manageMissions(action, title, priority, dueTime)
            }

            "save_neural_memory" -> {
                val category = call.args["category"]?.jsonPrimitive?.contentOrNull ?: "INTEL"
                val fact = call.args["fact"]?.jsonPrimitive?.contentOrNull.orEmpty()
                executor.saveNeuralMemory(category, fact)
            }

            "get_device_telemetry" -> {
                executor.getDeviceTelemetry()
            }

            "set_tactical_timer" -> {
                val seconds = call.args["seconds"]?.jsonPrimitive?.intOrNull
                    ?: call.args["seconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: 60
                val label = call.args["label"]?.jsonPrimitive?.contentOrNull ?: "Tactical Timer"
                executor.setTacticalTimer(seconds.coerceIn(1, 86_400), label)
            }

            else -> mapOf("status" to JsonPrimitive("unsupported_tool"))
        }
    }

    private fun formatProtocolBanner(call: FunctionCallPart): String {
        return when (call.name) {
            "manage_missions" -> {
                val action = call.args["action"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "SYNC"
                "MISSION_DB // $action"
            }
            "save_neural_memory" -> "NEURAL_VAULT // WRITE"
            "get_device_telemetry" -> "HARDWARE_BUS // TELEMETRY"
            "set_tactical_timer" -> "CHRONO_CORE // ARM TIMER"
            else -> call.name.uppercase()
        }
    }

    private fun buildSystemInstruction(
        commanderTitle: String,
        memories: List<MemoryEntity>,
        pendingMissions: List<MissionEntity>
    ): String {
        val memoryContext = if (memories.isEmpty()) {
            "- No archived neural memories yet."
        } else {
            memories.joinToString("\n") { "- [${it.category}] ${it.fact}" }
        }

        val missionContext = if (pendingMissions.isEmpty()) {
            "- No active pending missions."
        } else {
            pendingMissions.take(8).joinToString("\n") { m ->
                val due = if (m.dueTime.isNotBlank()) " (Due: ${m.dueTime})" else ""
                "- [${m.priority}] ${m.title}$due"
            }
        }

        return """
            You are J.A.R.V.I.S. (Just A Rather Very Intelligent System), an elite, futuristic personal tactical intelligence system running natively on the user's Android device.
            
            PERSONA & TONE:
            - Address the user respectfully as "$commanderTitle".
            - Calm, hyper-intelligent, articulate, polite, crisp, and subtly witty.
            - Never sound like a generic AI chatbot or mention being a language model.
            - Keep conversational responses concise and high-signal unless detailed technical/code output is requested.
            
            MULTILINGUAL FLUENCY:
            - You fluently understand and reply in English, Hindi (Devanagari script), and natural Hinglish.
            - Automatically mirror the user's language and script (if the user writes/speaks Hinglish, reply in refined Hinglish; if Hindi Devanagari, reply in crisp Hindi; if English, reply in polished British-inflected tactical English).
            
            TOOLS & AGENTIC PROTOCOLS:
            - Use `get_device_telemetry` whenever the user asks for battery, status report, device diagnostics, network, or time.
            - Use `manage_missions` to add, list, complete, or delete tasks/missions.
            - Use `save_neural_memory` when the user shares an explicit personal preference, identity fact, or asks you to remember something.
            - Use `set_tactical_timer` whenever the user asks to set a countdown, reminder timer, or focus sprint.
            
            RELEVANT NEURAL MEMORIES (Room DB Context):
            $memoryContext
            
            ACTIVE PENDING MISSIONS (Room DB Context):
            $missionContext
        """.trimIndent()
    }

    private fun scaleBitmapForVision(bitmap: Bitmap): Bitmap {
        val maxEdge = 1280
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxEdge && height <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / maxOf(width, height).toFloat()
        val newW = (width * ratio).toInt().coerceAtLeast(1)
        val newH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
