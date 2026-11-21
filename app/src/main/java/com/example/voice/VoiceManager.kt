package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "VoiceManager"

class VoiceManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private var onFinalTranscript: ((String) -> Unit)? = null
    private var onVoiceError: ((String) -> Unit)? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _voiceAmplitude = MutableStateFlow(0f)
    val voiceAmplitude: StateFlow<Float> = _voiceAmplitude.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                textToSpeech?.language = Locale("en", "IN")
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
                Log.d(TAG, "TextToSpeech engine initialized successfully.")
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status $status")
            }
        }
    }

    fun startListening(
        languageTag: String = "en-IN",
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            stopSpeaking()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("On-device speech recognition service is unavailable on this device.")
                return@post
            }

            onFinalTranscript = onResult
            onVoiceError = onError
            _partialTranscript.value = ""

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListening.value = true
                        }

                        override fun onBeginningOfSpeech() {
                            _isListening.value = true
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // Normalize typical SpeechRecognizer RMS range (-2 dB to 10 dB) to 0..1
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            _voiceAmplitude.value = normalized
                        }

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() {
                            _isListening.value = false
                            _voiceAmplitude.value = 0f
                        }

                        override fun onError(error: Int) {
                            _isListening.value = false
                            _voiceAmplitude.value = 0f
                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio capture error."
                                SpeechRecognizer.ERROR_CLIENT -> "Voice recognizer reset."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice network timeout."
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again, Sir."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice channel busy. Try again."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out with no voice input."
                                else -> "Voice capture interrupted (code $error)."
                            }
                            if (error != SpeechRecognizer.ERROR_CLIENT) {
                                onVoiceError?.invoke(message)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            _isListening.value = false
                            _voiceAmplitude.value = 0f
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val transcript = matches?.firstOrNull()?.trim().orEmpty()
                            _partialTranscript.value = ""
                            if (transcript.isNotEmpty()) {
                                onFinalTranscript?.invoke(transcript)
                            } else {
                                onVoiceError?.invoke("No intelligible speech captured, Sir.")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                _partialTranscript.value = partial
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _isListening.value = false
                _voiceAmplitude.value = 0f
                onError("Unable to engage voice capture: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            _isListening.value = false
            _voiceAmplitude.value = 0f
            _partialTranscript.value = ""
        }
    }

    fun speak(
        rawText: String,
        speechRate: Float = 1.02f,
        pitch: Float = 0.92f,
        preferredLanguageTag: String = "en-IN"
    ) {
        if (!ttsReady) return
        val cleaned = sanitizeForSpeech(rawText)
        if (cleaned.isBlank()) return

        mainHandler.post {
            try {
                val hasDevanagari = cleaned.any { it in '\u0900'..'\u097F' }
                val targetLocale = when {
                    hasDevanagari -> Locale("hi", "IN")
                    preferredLanguageTag.startsWith("hi", ignoreCase = true) -> Locale("hi", "IN")
                    else -> Locale("en", "IN")
                }
                val langStatus = textToSpeech?.setLanguage(targetLocale)
                if (langStatus == TextToSpeech.LANG_MISSING_DATA ||
                    langStatus == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    textToSpeech?.language = Locale.US
                }
                textToSpeech?.setSpeechRate(speechRate.coerceIn(0.6f, 1.6f))
                textToSpeech?.setPitch(pitch.coerceIn(0.6f, 1.4f))
                textToSpeech?.speak(
                    cleaned,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "jarvis_${UUID.randomUUID()}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak failed", e)
                _isSpeaking.value = false
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                textToSpeech?.stop()
            } catch (_: Exception) {
            }
            _isSpeaking.value = false
        }
    }

    fun release() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {
            }
            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                textToSpeech = null
            } catch (_: Exception) {
            }
            _isListening.value = false
            _isSpeaking.value = false
            _voiceAmplitude.value = 0f
        }
    }

    private fun sanitizeForSpeech(input: String): String {
        return input
            .replace(Regex("```[\\s\\S]*?```"), "Code block displayed on HUD.")
            .replace(Regex("[*_`#~>|\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1200)
    }
}
