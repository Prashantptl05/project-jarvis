package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiRepository
import com.example.ai.JarvisToolExecutor
import com.example.data.ChatMessageEntity
import com.example.data.CorePreferences
import com.example.data.CoreSettings
import com.example.data.JarvisDatabase
import com.example.data.JarvisLocalRepository
import com.example.data.MemoryEntity
import com.example.data.MissionEntity
import com.example.hardware.DeviceTelemetry
import com.example.hardware.DeviceTelemetryManager
import com.example.voice.VoiceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "JarvisViewModel"

enum class CoreVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class TacticalTimer(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val totalSeconds: Int,
    val remainingSeconds: Int
) {
    val formattedRemaining: String
        get() {
            val hrs = remainingSeconds / 3600
            val mins = (remainingSeconds % 3600) / 60
            val secs = remainingSeconds % 60
            return if (hrs > 0) {
                String.format("%02d:%02d:%02d", hrs, mins, secs)
            } else {
                String.format("%02d:%02d", mins, secs)
            }
        }
}

data class DestructiveActionRequest(
    val title: String,
    val description: String,
    val protocolCode: String
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JarvisDatabase.getInstance(application)
    private val localRepo = JarvisLocalRepository(database)
    private val corePrefs = CorePreferences(application)
    private val telemetryManager = DeviceTelemetryManager(application)
    private val voiceManager = VoiceManager(application)
    private val geminiRepo = GeminiRepository()

    val telemetry: StateFlow<DeviceTelemetry> = telemetryManager.telemetry

    val settings: StateFlow<CoreSettings> = corePrefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CoreSettings()
    )

    val memories: StateFlow<List<MemoryEntity>> = localRepo.memories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val missions: StateFlow<List<MissionEntity>> = localRepo.missions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val messages: StateFlow<List<ChatMessageEntity>> = localRepo.messages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val voiceAmplitude: StateFlow<Float> = voiceManager.voiceAmplitude
    val partialTranscript: StateFlow<String> = voiceManager.partialTranscript

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val coreVisualState: StateFlow<CoreVisualState> = combine(
        voiceManager.isListening,
        _isThinking,
        voiceManager.isSpeaking
    ) { listening, thinking, speaking ->
        when {
            listening -> CoreVisualState.LISTENING
            thinking -> CoreVisualState.THINKING
            speaking -> CoreVisualState.SPEAKING
            else -> CoreVisualState.IDLE
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CoreVisualState.IDLE
    )

    private val _executingProtocol = MutableStateFlow<String?>(null)
    val executingProtocol: StateFlow<String?> = _executingProtocol.asStateFlow()

    private val _activeTimers = MutableStateFlow<List<TacticalTimer>>(emptyList())
    val activeTimers: StateFlow<List<TacticalTimer>> = _activeTimers.asStateFlow()

    private val _attachedImage = MutableStateFlow<Bitmap?>(null)
    val attachedImage: StateFlow<Bitmap?> = _attachedImage.asStateFlow()

    private val _hudNotice = MutableStateFlow<String?>(null)
    val hudNotice: StateFlow<String?> = _hudNotice.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<DestructiveActionRequest?>(null)
    val pendingConfirmation: StateFlow<DestructiveActionRequest?> = _pendingConfirmation.asStateFlow()

    private var confirmationDeferred: CompletableDeferred<Boolean>? = null
    private val timerJobs = mutableMapOf<String, Job>()

    init {
        telemetryManager.startMonitoring()
        seedInitialBootMessageIfEmpty()
    }

    private fun seedInitialBootMessageIfEmpty() {
        viewModelScope.launch {
            val recent = localRepo.getRecentChatHistory(1)
            if (recent.isEmpty()) {
                localRepo.insertMessage(
                    ChatMessageEntity(
                        role = "JARVIS",
                        content = "Good evening, Sir. All neural subsystems and local telemetry sensors are online. Tap the Arc-Reactor Core to speak in English, Hindi, or Hinglish—or transmit your command below.",
                        modelBadge = "SYSTEM // ONLINE"
                    )
                )
            }
        }
    }

    fun dismissNotice() {
        _hudNotice.value = null
    }

    fun postNotice(message: String) {
        _hudNotice.value = message
    }

    fun attachBitmap(bitmap: Bitmap?) {
        _attachedImage.value = bitmap
    }

    fun attachImageFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val decoded = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (decoded != null) {
                    _attachedImage.value = decoded
                } else {
                    _hudNotice.value = "Unable to decode selected visual feed, Sir."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image from URI", e)
                _hudNotice.value = "Visual feed load failed: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun clearAttachedImage() {
        _attachedImage.value = null
    }

    fun toggleVoiceCapture() {
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
        } else {
            val lang = settings.value.voiceLanguage
            voiceManager.startListening(
                languageTag = lang,
                onResult = { transcript ->
                    submitUserCommand(transcript)
                },
                onError = { errorMsg ->
                    _hudNotice.value = errorMsg
                }
            )
        }
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun toggleTtsMuted() {
        viewModelScope.launch {
            val next = !settings.value.ttsEnabled
            corePrefs.setTtsEnabled(next)
            if (!next) voiceManager.stopSpeaking()
        }
    }

    fun toggleOverclock() {
        viewModelScope.launch {
            val next = !settings.value.overclockEnabled
            corePrefs.setOverclockEnabled(next)
            _hudNotice.value = if (next) {
                "OVERCLOCK ENGAGED // Gemini 4 Argon deep reasoning active."
            } else {
                "TACTICAL MODE // Gemini 3.8 Flash low-latency core active."
            }
        }
    }

    fun updateVoiceLanguage(languageTag: String) {
        viewModelScope.launch { corePrefs.setVoiceLanguage(languageTag) }
    }

    fun updateTtsRate(rate: Float) {
        viewModelScope.launch { corePrefs.setTtsSpeechRate(rate) }
    }

    fun updateTtsPitch(pitch: Float) {
        viewModelScope.launch { corePrefs.setTtsPitch(pitch) }
    }

    fun updateCommanderTitle(title: String) {
        viewModelScope.launch { corePrefs.setCommanderTitle(title) }
    }

    fun updateAutoMemoryExtraction(enabled: Boolean) {
        viewModelScope.launch { corePrefs.setAutoMemoryExtraction(enabled) }
    }

    fun respondToConfirmation(authorized: Boolean) {
        confirmationDeferred?.complete(authorized)
        confirmationDeferred = null
        _pendingConfirmation.value = null
    }

    fun submitUserCommand(rawText: String) {
        val text = rawText.trim()
        val imageToSend = _attachedImage.value
        if (text.isEmpty() && imageToSend == null) return
        if (_isThinking.value) {
            _hudNotice.value = "Neural core is currently processing a protocol, Sir."
            return
        }

        _attachedImage.value = null
        _hudNotice.value = null
        voiceManager.stopSpeaking()

        viewModelScope.launch {
            val savedImagePath = imageToSend?.let { saveBitmapThumbnail(it) }
            val displayPrompt = text.ifBlank { "[VISUAL SCAN FEED ATTACHED]" }

            val recentHistory = localRepo.getRecentChatHistory(12)
            localRepo.insertMessage(
                ChatMessageEntity(
                    role = "USER",
                    content = displayPrompt,
                    imagePath = savedImagePath
                )
            )

            _isThinking.value = true
            val currentSettings = settings.value

            try {
                val relevantMemories = localRepo.getRelevantMemories(text)
                val pendingMissions = localRepo.getPendingMissions()

                val toolExecutor = buildToolExecutor()
                val result = geminiRepo.sendCommand(
                    userPrompt = text,
                    attachedImage = imageToSend,
                    recentHistory = recentHistory,
                    relevantMemories = relevantMemories,
                    pendingMissions = pendingMissions,
                    settings = currentSettings,
                    toolExecutor = toolExecutor,
                    onProtocolExecuting = { banner -> _executingProtocol.value = banner }
                )

                val toolLogSummary = result.toolLogs
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(" • ")

                localRepo.insertMessage(
                    ChatMessageEntity(
                        role = "JARVIS",
                        content = result.replyText,
                        toolCallLog = toolLogSummary,
                        modelBadge = result.modelLabel
                    )
                )

                if (currentSettings.ttsEnabled) {
                    voiceManager.speak(
                        rawText = result.replyText,
                        speechRate = currentSettings.ttsSpeechRate,
                        pitch = currentSettings.ttsPitch,
                        preferredLanguageTag = currentSettings.voiceLanguage
                    )
                }

                // Background structured JSON memory extraction if no explicit save_neural_memory tool ran
                if (currentSettings.autoMemoryExtraction &&
                    text.isNotBlank() &&
                    result.toolLogs.none { it.contains("NEURAL_VAULT") }
                ) {
                    launch {
                        val extracted = geminiRepo.extractMemoryIfWorthy(text)
                        if (extracted != null) {
                            localRepo.saveMemory(extracted.category, extracted.fact)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Command processing error", e)
                val errorMsg = e.localizedMessage ?: "Neural inference interrupted."
                _hudNotice.value = errorMsg
                localRepo.insertMessage(
                    ChatMessageEntity(
                        role = "SYSTEM",
                        content = "FAULT // $errorMsg",
                        modelBadge = "ERROR // LINK"
                    )
                )
            } finally {
                _isThinking.value = false
                _executingProtocol.value = null
            }
        }
    }

    private fun buildToolExecutor(): JarvisToolExecutor = object : JarvisToolExecutor {
        override suspend fun manageMissions(
            action: String,
            title: String?,
            priority: String?,
            dueTime: String?
        ): Map<String, JsonPrimitive> {
            return when (action.lowercase()) {
                "add" -> {
                    val cleanTitle = title?.trim().orEmpty()
                    if (cleanTitle.isBlank()) {
                        mapOf("status" to JsonPrimitive("error"), "message" to JsonPrimitive("Mission title required"))
                    } else {
                        val saved = localRepo.addMission(
                            title = cleanTitle,
                            priority = priority ?: "NORMAL",
                            dueTime = dueTime.orEmpty()
                        )
                        mapOf(
                            "status" to JsonPrimitive("added"),
                            "id" to JsonPrimitive(saved.id),
                            "title" to JsonPrimitive(saved.title),
                            "priority" to JsonPrimitive(saved.priority),
                            "due_time" to JsonPrimitive(saved.dueTime)
                        )
                    }
                }

                "complete" -> {
                    val query = title?.trim().orEmpty()
                    val completed = if (query.isNotBlank()) localRepo.completeMissionByTitle(query) else null
                    if (completed != null) {
                        mapOf(
                            "status" to JsonPrimitive("completed"),
                            "title" to JsonPrimitive(completed.title)
                        )
                    } else {
                        mapOf(
                            "status" to JsonPrimitive("not_found"),
                            "message" to JsonPrimitive("No mission matched '$query'")
                        )
                    }
                }

                "delete" -> {
                    val query = title?.trim().orEmpty()
                    val target = if (query.isNotBlank()) localRepo.findMissionByTitle(query) else null
                    if (target == null) {
                        mapOf(
                            "status" to JsonPrimitive("not_found"),
                            "message" to JsonPrimitive("No mission matched '$query'")
                        )
                    } else {
                        val approved = requestHumanAuthorization(
                            DestructiveActionRequest(
                                title = "PURGE MISSION RECORD",
                                description = "Authorize permanent deletion of mission \"${target.title}\" [${target.priority}] from Mission Control?",
                                protocolCode = "MISSION_DB // DELETE"
                            )
                        )
                        if (approved) {
                            localRepo.deleteMission(target)
                            mapOf(
                                "status" to JsonPrimitive("deleted"),
                                "title" to JsonPrimitive(target.title)
                            )
                        } else {
                            mapOf(
                                "status" to JsonPrimitive("aborted_by_user"),
                                "message" to JsonPrimitive("Commander declined deletion of '${target.title}'.")
                            )
                        }
                    }
                }

                else -> {
                    val all = localRepo.getAllMissionsSnapshot()
                    val pending = all.filter { !it.isCompleted }
                    val summary = if (pending.isEmpty()) {
                        "No pending missions."
                    } else {
                        pending.joinToString("; ") { m ->
                            val due = if (m.dueTime.isNotBlank()) " @ ${m.dueTime}" else ""
                            "[${m.priority}] ${m.title}$due"
                        }
                    }
                    mapOf(
                        "status" to JsonPrimitive("ok"),
                        "pending_count" to JsonPrimitive(pending.size),
                        "completed_count" to JsonPrimitive(all.size - pending.size),
                        "pending_missions" to JsonPrimitive(summary)
                    )
                }
            }
        }

        override suspend fun saveNeuralMemory(
            category: String,
            fact: String
        ): Map<String, JsonPrimitive> {
            if (fact.isBlank()) {
                return mapOf("status" to JsonPrimitive("error"), "message" to JsonPrimitive("Fact cannot be blank"))
            }
            val saved = localRepo.saveMemory(category, fact)
            return mapOf(
                "status" to JsonPrimitive("archived"),
                "category" to JsonPrimitive(saved.category),
                "fact" to JsonPrimitive(saved.fact)
            )
        }

        override suspend fun getDeviceTelemetry(): Map<String, JsonPrimitive> {
            val snap = telemetryManager.readCurrentSnapshot()
            val pendingCount = localRepo.getPendingMissions().size
            val memoryCount = memories.value.size
            return mapOf(
                "battery_percent" to JsonPrimitive(snap.batteryPercent),
                "is_charging" to JsonPrimitive(snap.isCharging),
                "network_status" to JsonPrimitive(snap.networkStatus),
                "is_online" to JsonPrimitive(snap.isOnline),
                "device_model" to JsonPrimitive(snap.deviceModel),
                "android_version" to JsonPrimitive(snap.androidVersion),
                "local_time" to JsonPrimitive(snap.formattedLocalTime()),
                "pending_missions" to JsonPrimitive(pendingCount),
                "archived_memories" to JsonPrimitive(memoryCount),
                "active_timers" to JsonPrimitive(_activeTimers.value.size)
            )
        }

        override suspend fun setTacticalTimer(
            seconds: Int,
            label: String
        ): Map<String, JsonPrimitive> {
            val timer = startTacticalTimer(seconds, label)
            return mapOf(
                "status" to JsonPrimitive("armed"),
                "timer_id" to JsonPrimitive(timer.id),
                "label" to JsonPrimitive(timer.label),
                "duration_seconds" to JsonPrimitive(timer.totalSeconds)
            )
        }
    }

    private suspend fun requestHumanAuthorization(request: DestructiveActionRequest): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmationDeferred = deferred
        _pendingConfirmation.value = request
        return deferred.await()
    }

    fun startTacticalTimer(seconds: Int, label: String): TacticalTimer {
        val safeSeconds = seconds.coerceIn(1, 86_400)
        val cleanLabel = label.trim().ifBlank { "Tactical Countdown" }
        val timer = TacticalTimer(
            label = cleanLabel,
            totalSeconds = safeSeconds,
            remainingSeconds = safeSeconds
        )
        _activeTimers.value = _activeTimers.value + timer

        val job = viewModelScope.launch {
            var remaining = safeSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                _activeTimers.value = _activeTimers.value.map {
                    if (it.id == timer.id) it.copy(remainingSeconds = remaining) else it
                }
            }
            // Timer completed!
            telemetryManager.triggerTacticalTimerAlert()
            _activeTimers.value = _activeTimers.value.filterNot { it.id == timer.id }
            timerJobs.remove(timer.id)

            val alertText = "Chrono Alert, ${settings.value.commanderTitle}: Timer \"${timer.label}\" has reached zero."
            _hudNotice.value = alertText
            localRepo.insertMessage(
                ChatMessageEntity(
                    role = "SYSTEM",
                    content = "CHRONO COMPLETE // ${timer.label}",
                    modelBadge = "TIMER // ALERT"
                )
            )
            if (settings.value.ttsEnabled) {
                voiceManager.speak(
                    rawText = alertText,
                    speechRate = settings.value.ttsSpeechRate,
                    pitch = settings.value.ttsPitch,
                    preferredLanguageTag = settings.value.voiceLanguage
                )
            }
        }
        timerJobs[timer.id] = job
        return timer
    }

    fun cancelTacticalTimer(timerId: String) {
        timerJobs.remove(timerId)?.cancel()
        _activeTimers.value = _activeTimers.value.filterNot { it.id == timerId }
    }

    // Manual Room CRUD actions for Neural Memory Vault & Mission Control screens
    fun addOrUpdateMemory(existing: MemoryEntity?, category: String, fact: String) {
        if (fact.isBlank()) return
        viewModelScope.launch {
            if (existing != null) {
                localRepo.updateMemory(existing.copy(category = category.uppercase(), fact = fact.trim()))
            } else {
                localRepo.saveMemory(category, fact)
            }
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch { localRepo.deleteMemory(memory) }
    }

    fun wipeAllMemories() {
        viewModelScope.launch {
            localRepo.wipeAllMemories()
            _hudNotice.value = "Neural Memory Vault purged, Sir."
        }
    }

    fun addMissionManual(title: String, priority: String, dueTime: String) {
        if (title.isBlank()) return
        viewModelScope.launch { localRepo.addMission(title, priority, dueTime) }
    }

    fun toggleMissionCompleted(mission: MissionEntity) {
        viewModelScope.launch { localRepo.toggleMissionCompleted(mission) }
    }

    fun deleteMissionManual(mission: MissionEntity) {
        viewModelScope.launch { localRepo.deleteMission(mission) }
    }

    fun clearCompletedMissions() {
        viewModelScope.launch { localRepo.clearCompletedMissions() }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            localRepo.clearChatHistory()
            seedInitialBootMessageIfEmpty()
        }
    }

    private suspend fun saveBitmapThumbnail(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(getApplication<Application>().filesDir, "vision_captures")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Unable to persist vision thumbnail", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
        telemetryManager.stopMonitoring()
        voiceManager.release()
    }
}
