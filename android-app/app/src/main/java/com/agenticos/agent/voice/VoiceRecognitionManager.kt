package com.agenticos.agent.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Multi-tier voice recognition pipeline for near-perfect accuracy.
 * 
 * Architecture:
 * [Mic] → [Audio Preprocessing] → [VAD] → [Wake Word: Porcupine] 
 *                                      │
 *                                      ▼
 *                             [STT Engine Selection]
 *                             ┌─────────┼─────────┐
 *                             ▼         ▼         ▼
 *                        Cloud STT  System STT  Offline STT
 *                     (Google API) (SpeechRec) (Whisper.cpp)
 *                             │         │         │
 *                             └─────────┼─────────┘
 *                                       ▼
 *                               [Confidence Scoring]
 *                                       │
 *                     ┌─────────────────┼─────────────────┐
 *                     ▼                 ▼                 ▼
 *                > 0.85            0.60–0.85          < 0.60
 *              Accept + LLM       Retry w/          Ask repeat
 *              fuzzy correction   different tier
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val onResult: (RecognitionResult) -> Unit,
    private val onError: (RecognitionError) -> Unit,
    private val onAudioLevel: (Float) -> Unit,
    private val onWakeWord: () -> Unit
) {
    companion object {
        private const val TAG = "VoiceRecognitionManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 512 // 32ms at 16kHz
        const val BUFFER_SIZE = 4096
        const val MAX_SILENCE_FRAMES = 50 // ~1.6s silence to end command
    }

    // State
    private var audioRecord: AudioRecord? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var porcupine: PorcupineWrapper? = null
    private var vadDetector: VadDetectorWrapper? = null
    
    private val isListening = AtomicBoolean(false)
    private val isWakeWordActive = AtomicBoolean(false)
    private val isCommandMode = AtomicBoolean(false)
    
    // Audio buffers
    private val audioBuffer = mutableListOf<ShortArray>()
    private var silenceFrames = 0
    private var speechStarted = false
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // TTS
    private val ttsQueue = Channel<String>(capacity = 20)
    private var ttsReady = false
    private var currentUtteranceId: String? = null
    
    // Configuration
    private var useCloudSTT = true
    private var userPrefersCloud = true
    private var phraseHints: List<String> = emptyList()
    private var voiceProfile: VoiceProfile? = null
    
    // Cloud STT client
    private val cloudSTTClient: CloudSTTClient? = CloudSTTClient()
    
    // Offline STT client
    private val offlineSTTClient: OfflineSTTClient? = OfflineSTTClient()

    init {
        initializeAudioPipeline()
        initializeTTS()
        loadVoiceProfile()
    }

    // ============================================================
    // INITIALIZATION
    // ============================================================
    
    private fun initializeAudioPipeline() {
        // 1. Audio preprocessing chain (WebRTC)
        // Note: Actual WebRTC NS/AEC/AGC would be via JNI
        // For now, we use Android's built-in effects as fallback
        
        // 2. Voice Activity Detection
        vadDetector = VadDetectorWrapper()
        
        // 3. Wake word detection (Porcupine)
        porcupine = PorcupineWrapper(context) { keywordIndex ->
            if (keywordIndex >= 0) {
                onWakeWordDetected()
            }
        }
        
        // 4. System SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(systemRecognitionListener)
        }
        
        Log.d(TAG, "Audio pipeline initialized")
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val langResult = tts?.setLanguage(Locale.getDefault())
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.speechRate = 1.0f
                tts?.pitch = 1.0f
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                currentUtteranceId = utteranceId
            }
            override fun onDone(utteranceId: String) {
                if (utteranceId == currentUtteranceId) currentUtteranceId = null
            }
            override fun onError(utteranceId: String) {
                if (utteranceId == currentUtteranceId) currentUtteranceId = null
            }
        })
        
        // Start TTS processor
        scope.launch {
            for (text in ttsQueue) {
                if (ttsReady && tts != null) {
                    val utteranceId = "utterance_${System.currentTimeMillis()}"
                    val params = android.os.Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    }
                    tts?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
                }
            }
        }
    }
    
    private fun loadVoiceProfile() {
        // Load from Room database
        // voiceProfile = VoiceProfileDao.load(context)
        // For now, use defaults
        voiceProfile = VoiceProfile(
            userId = "default",
            customVocabulary = emptyList(),
            aliases = emptyMap()
        )
        
        buildPhraseHints()
    }
    
    private fun buildPhraseHints() {
        val hints = mutableListOf<String>()
        
        // Core tool vocabulary
        hints.addAll(listOf(
            "open", "launch", "start", "close",
            "play", "pause", "skip", "next", "previous",
            "set", "create", "make", "remind",
            "text", "message", "sms", "call", "dial", "phone",
            "wifi", "wi-fi", "bluetooth", "bt",
            "silent", "vibrate", "normal", "loud",
            "alarm", "timer", "clock",
            "weather", "news", "search", "google",
            "summarize", "explain", "what is", "who is",
            "today", "tomorrow", "yesterday",
            "morning", "afternoon", "evening", "night",
            "home", "work", "office", "school"
        ))
        
        // User contacts (top 20)
        hints.addAll(getTopContacts(20).map { it.name })
        
        // User aliases
        hints.addAll(voiceProfile?.aliases?.keys ?: emptyList())
        
        // Custom vocabulary
        hints.addAll(voiceProfile?.customVocabulary ?: emptyList())
        
        phraseHints = hints.distinct()
    }
    
    // ============================================================
    // PUBLIC API
    // ============================================================
    
    /** Start the complete listening pipeline (wake word + command) */
    fun startListening() {
        if (isListening.getAndSet(true)) return
        
        // Start audio recording
        startAudioRecording()
        
        // Start wake word detection
        porcupine?.start()
        isWakeWordActive.set(true)
        
        Log.d(TAG, "Started listening pipeline")
    }
    
    /** Stop all listening */
    fun stopListening() {
        isListening.set(false)
        isWakeWordActive.set(false)
        isCommandMode.set(false)
        
        porcupine?.stop()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        // Reset state
        audioBuffer.clear()
        speechStarted = false
        silenceFrames = 0
        
        Log.d(TAG, "Stopped listening pipeline")
    }
    
    /** Start listening for a command (after wake word detected) */
    fun startCommandMode() {
        isCommandMode.set(true)
        isWakeWordActive.set(false)
        speechStarted = false
        silenceFrames = 0
        audioBuffer.clear()
        
        // Stop Porcupine temporarily
        porcupine?.pause()
        
        Log.d(TAG, "Entered command mode")
    }
    
    /** Speak text (queued) */
    fun speak(text: String) {
        if (text.isNotBlank()) {
            ttsQueue.trySend(text)
        }
    }
    
    /** Interrupt current speech */
    fun stopSpeaking() {
        tts?.stop()
        currentUtteranceId = null
        while (!ttsQueue.isClosedForSend) {
            ttsQueue.tryReceive()?.let { /* discard */ } ?: break
        }
    }
    
    /** Check if currently speaking */
    fun isSpeaking(): Boolean = currentUtteranceId != null
    
    /** Update phrase hints dynamically */
    fun updatePhraseHints(newHints: List<String>) {
        val current = phraseHints.toMutableSet()
        current.addAll(newHints)
        phraseHints = current.toList()
    }
    
    /** Set cloud STT preference */
    fun setCloudSTTEnabled(enabled: Boolean) {
        userPrefersCloud = enabled
    }
    
    /** Shutdown */
    fun shutdown() {
        stopListening()
        scope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.shutdown()
        tts = null
        porcupine?.delete()
        porcupine = null
        vadDetector?.close()
        vadDetector = null
        offlineSTTClient?.shutdown()
        ttsQueue.close()
    }

    // ============================================================
    // AUDIO RECORDING & PROCESSING
    // ============================================================
    
    private fun startAudioRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ).coerceAtLeast(BUFFER_SIZE)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            onError(RecognitionError.AUDIO_UNAVAILABLE)
            return
        }
        
        audioRecord?.startRecording()
        
        // Start processing loop
        scope.launch {
            processAudioLoop()
        }
    }
    
    private fun processAudioLoop() {
        val buffer = ShortArray(FRAME_SIZE)
        
        while (isListening.get()) {
            val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: 0
            
            if (read <= 0) {
                delay(10)
                continue
            }
            
            // Calculate audio level for UI feedback
            val level = calculateRMS(buffer, read)
            withContext(Dispatchers.Main) {
                onAudioLevel(level)
            }
            
            // Apply audio preprocessing
            val processedBuffer = applyAudioPreprocessing(buffer, read)
            
            if (isWakeWordActive.get()) {
                // Wake word detection mode
                porcupine?.process(processedBuffer)
            } else if (isCommandMode.get()) {
                // Command recording mode
                processCommandAudio(processedBuffer)
            }
        }
    }
    
    private fun applyAudioPreprocessing(input: ShortArray, size: Int): ShortArray {
        // In production: WebRTC NoiseSuppressor, AcousticEchoCanceler, AutomaticGainControl
        // For now, return input as-is (placeholder for JNI integration)
        return input.copyOf(size)
    }
    
    private fun calculateRMS(buffer: ShortArray, size: Int): Float {
        var sum = 0L
        for (i in 0 until size) {
            sum += (buffer[i] * buffer[i]).toLong()
        }
        val rms = Math.sqrt(sum.toDouble() / size)
        return (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
    }
    
    private fun processCommandAudio(buffer: ShortArray) {
        // VAD detection
        val isSpeech = vadDetector?.detect(buffer) ?: true
        
        if (isSpeech) {
            if (!speechStarted) {
                speechStarted = true
                silenceFrames = 0
            }
            audioBuffer.add(buffer.copyOf())
        } else {
            if (speechStarted) {
                silenceFrames++
                audioBuffer.add(buffer.copyOf())
                
                if (silenceFrames >= MAX_SILENCE_FRAMES) {
                    // End of speech detected
                    finishCommandRecording()
                }
            }
        }
    }
    
    private fun finishCommandRecording() {
        isCommandMode.set(false)
        
        if (audioBuffer.isNotEmpty()) {
            scope.launch {
                val audioData = flattenAudioBuffer()
                recognizeSpeech(audioData)
            }
        }
        
        // Resume wake word detection
        audioBuffer.clear()
        speechStarted = false
        silenceFrames = 0
        porcupine?.resume()
        isWakeWordActive.set(true)
    }
    
    private fun flattenAudioBuffer(): ByteArray {
        val totalSize = audioBuffer.sumOf { it.size }
        val result = ByteArray(totalSize * 2) // 2 bytes per short
        var offset = 0
        
        for (chunk in audioBuffer) {
            for (sample in chunk) {
                result[offset] = (sample.toInt() and 0xFF).toByte()
                result[offset + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                offset += 2
            }
        }
        
        audioBuffer.clear()
        return result
    }

    // ============================================================
    // WAKE WORD DETECTION
    // ============================================================
    
    private fun onWakeWordDetected() {
        Log.d(TAG, "Wake word detected!")
        
        withContext(Dispatchers.Main) {
            onWakeWord()
        }
        
        // Switch to command mode
        startCommandMode()
    }

    // ============================================================
    // SPEECH RECOGNITION (MULTI-TIER)
    // ============================================================
    
    private suspend fun recognizeSpeech(audioData: ByteArray) {
        var result: RecognitionResult? = null
        
        // Tier 1: Cloud STT (Google Speech-to-Text)
        if (userPrefersCloud && cloudSTTClient != null && isNetworkAvailable()) {
            Log.d(TAG, "Trying Cloud STT...")
            result = cloudSTTClient!!.recognize(audioData, phraseHints)
            
            if (result != null && result.confidence > 0.85) {
                Log.d(TAG, "Cloud STT success: ${result.text} (conf: ${result.confidence})")
                processResult(result)
                return
            }
        }
        
        // Tier 2: System SpeechRecognizer
        Log.d(TAG, "Trying System STT...")
        result = recognizeWithSystem(audioData)
        
        if (result != null && result.confidence > 0.70) {
            Log.d(TAG, "System STT success: ${result.text} (conf: ${result.confidence})")
            processResult(result)
            return
        }
        
        // Tier 3: Offline Whisper.cpp
        if (offlineSTTClient != null) {
            Log.d(TAG, "Trying Offline STT...")
            result = offlineSTTClient!!.recognize(audioData, phraseHints)
            
            if (result != null && result.confidence > 0.60) {
                Log.d(TAG, "Offline STT success: ${result.text} (conf: ${result.confidence})")
                processResult(result)
                return
            }
        }
        
        // All tiers failed or low confidence
        val finalResult = result ?: RecognitionResult(
            text = "",
            confidence = 0f,
            isFinal = true,
            alternatives = emptyList(),
            needsRepeat = true
        )
        
        processResult(finalResult)
    }
    
    private suspend fun recognizeWithSystem(audioData: ByteArray): RecognitionResult? {
        return suspendCancellableCoroutine { cont ->
            if (speechRecognizer == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            }
            
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {}
                
                override fun onError(error: Int) {
                    cont.resume(RecognitionResult(
                        text = "",
                        confidence = 0f,
                        isFinal = true,
                        alternatives = emptyList(),
                        needsRepeat = true
                    ))
                }
                
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    val alternatives = matches?.drop(1).toList() ?: emptyList()
                    
                    // Estimate confidence (system doesn't provide it reliably)
                    val confidence = if (text.isNotBlank()) 0.75f else 0f
                    
                    cont.resume(RecognitionResult(
                        text = text,
                        confidence = confidence,
                        isFinal = true,
                        alternatives = alternatives
                    ))
                }
                
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            }
            
            speechRecognizer?.setRecognitionListener(listener)
            speechRecognizer?.startListening(intent)
        }
    }
    
    private suspend fun processResult(result: RecognitionResult) {
        // Apply LLM fuzzy correction
        val corrected = if (result.confidence > 0.50) {
            correctWithLLM(result.text, result.confidence)
        } else {
            CorrectedCommand(
                text = result.text,
                tool = null,
                confidence = result.confidence
            )
        }
        
        val finalResult = result.copy(
            text = corrected.text,
            confidence = corrected.confidence,
            needsRepeat = result.confidence < 0.60
        )
        
        withContext(Dispatchers.Main) {
            onResult(finalResult)
        }
    }
    
    private suspend fun correctWithLLM(rawText: String, originalConfidence: Float): CorrectedCommand {
        // In production: call LLMManager to correct STT errors
        // For now, return as-is with same confidence
        return CorrectedCommand(
            text = rawText,
            tool = null,
            confidence = originalConfidence
        )
    }
    
    // ============================================================
    // SYSTEM RECOGNITION LISTENER (for continuous mode)
    // ============================================================
    
    private val systemRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        
        override fun onEndOfSpeech() {
            // Handled by our VAD logic
        }
        
        override fun onError(error: Int) {
            withContext(Dispatchers.Main) {
                onError(mapErrorCode(error))
            }
        }
        
        override fun onResults(results: android.os.Bundle?) {
            // Not used in our custom flow
        }
        
        override fun onPartialResults(partialResults: android.os.Bundle?) {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    }
    
    private fun mapErrorCode(code: Int): RecognitionError {
        return when (code) {
            SpeechRecognizer.ERROR_NETWORK -> RecognitionError.NO_NETWORK
            SpeechRecognizer.ERROR_AUDIO -> RecognitionError.AUDIO_UNAVAILABLE
            SpeechRecognizer.ERROR_NO_MATCH -> RecognitionError.LOW_CONFIDENCE
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RecognitionError.TIMEOUT
            else -> RecognitionError.UNKNOWN
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    private fun getTopContacts(limit: Int): List<Contact> {
        // Query ContactsContract for most frequent contacts
        // Placeholder
        return emptyList()
    }
    
    // ============================================================
    // DATA CLASSES
    // ============================================================
    
    data class RecognitionResult(
        val text: String,
        val confidence: Float,
        val isFinal: Boolean,
        val alternatives: List<String> = emptyList(),
        val needsRepeat: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class CorrectedCommand(
        val text: String,
        val tool: String?,
        val confidence: Float
    )
    
    data class VoiceProfile(
        val userId: String,
        val customVocabulary: List<String>,
        val aliases: Map<String, String>
    )
    
    data class Contact(
        val name: String,
        val phoneNumber: String,
        val frequency: Int
    )
    
    enum class RecognitionError {
        NO_NETWORK,
        LOW_CONFIDENCE,
        AUDIO_UNAVAILABLE,
        TIMEOUT,
        UNKNOWN
    }
}

// ============================================================
// PLACEHOLDER WRAPPERS (to be replaced with actual implementations)
// ============================================================

class PorcupineWrapper(
    context: Context,
    private val onKeyword: (Int) -> Unit
) {
    private var porcupine: Any? = null // Actual: ai.picovoice.porcupine.Porcupine
    
    init {
        // Initialize Porcupine with access key and keyword files
        // porcupine = Porcupine.Builder().setAccessKey(...).setKeywordPaths(...).build(context)
    }
    
    fun start() { /* porcupine?.start() */ }
    fun pause() { /* porcupine?.pause() */ }
    fun resume() { /* porcupine?.resume() */ }
    fun process(buffer: ShortArray) { 
        /* val result = porcupine?.process(buffer); if (result >= 0) onKeyword(result) */
    }
    fun stop() { /* porcupine?.stop() */ }
    fun delete() { /* porcupine?.delete() */ }
}

class VadDetectorWrapper {
    private var vad: Any? = null // Actual: com.google.mediapipe.tasks.vad.VadDetector
    
    init {
        // Initialize MediaPipe VAD
        // vad = VadDetector.createFromOptions(...)
    }
    
    fun detect(buffer: ShortArray): Boolean {
        // val result = vad?.detect(buffer)
        // return result?.speechProbability ?: 0.5 > 0.5
        return true // Placeholder
    }
    
    fun close() { /* vad?.close() */ }
}

class CloudSTTClient {
    suspend fun recognize(
        audioData: ByteArray,
        phraseHints: List<String>
    ): VoiceRecognitionManager.RecognitionResult? {
        // Google Cloud Speech-to-Text API call
        // POST https://speech.googleapis.com/v1/speech:recognize
        // with speechContexts: [{phrases: phraseHints, boost: 20}]
        // model: "command_and_search", useEnhanced: true
        return null // Placeholder
    }
}

class OfflineSTTClient {
    private var whisper: Any? = null // Actual: whisper.cpp wrapper
    
    init {
        // Load whisper model: whisper-base.en.bin or whisper-small.en.bin
    }
    
    suspend fun recognize(
        audioData: ByteArray,
        phraseHints: List<String>
    ): VoiceRecognitionManager.RecognitionResult? {
        // whisper?.recognize(audioData, "en")
        return null // Placeholder
    }
    
    fun shutdown() { /* whisper?.free() */ }
}