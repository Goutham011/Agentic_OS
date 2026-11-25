package com.agenticos.agent.voice

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private val ttsQueue = Channel<String>(capacity = 10)
    private val recognitionChannel = Channel<String>(capacity = 1)
    
    // TTS State
    private var ttsReady = false
    private var currentUtteranceId: String? = null
    
    init {
        initTTS()
        initSpeechRecognizer()
        startTTSProcessor()
    }
    
    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val langResult = tts?.setLanguage(Locale.getDefault())
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to US English
                    tts?.setLanguage(Locale.US)
                }
                // Set speech rate and pitch
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
    }
    
    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            
            override fun onBeginningOfSpeech() {}
            
            override fun onRmsChanged(rmsdB: Float) {}
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                isListening = false
            }
            
            override fun onError(error: Int) {
                isListening = false
                recognitionChannel.trySend("ERROR:$error")
            }
            
            override fun onResults(results: android.os.Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                recognitionChannel.trySend(text)
            }
            
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
    }
    
    private fun startTTSProcessor() {
        CoroutineScope(Dispatchers.IO).launch {
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
    
    // ===== PUBLIC API =====
    
    /** Start listening for a single command (after hotword) */
    suspend fun listenForCommand(): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (speechRecognizer == null) return@withContext "ERROR: SpeechRecognizer not available"
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }
        
        isListening = true
        speechRecognizer?.startListening(intent)
        
        return@withContext recognitionChannel.receive()
    }
    
    /** Start continuous listening for hotword (requires Porcupine or similar) */
    fun startHotwordDetection(onHotword: (String) -> Unit) {
        // In production, integrate Porcupine wake word engine here
        // This is a placeholder
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
        // Clear queue
        while (!ttsQueue.isClosedForSend) {
            ttsQueue.tryReceive()?.let { /* discard */ } ?: break
        }
    }
    
    /** Check if currently speaking */
    fun isSpeaking(): Boolean = currentUtteranceId != null
    
    /** Set language */
    fun setLanguage(locale: Locale): Boolean {
        return tts?.setLanguage(locale) == TextToSpeech.SUCCESS
    }
    
    /** Set speech rate (0.5 - 2.0) */
    fun setSpeechRate(rate: Float) {
        tts?.speechRate = rate.coerceIn(0.5f, 2.0f)
    }
    
    /** Set pitch (0.5 - 2.0) */
    fun setPitch(pitch: Float) {
        tts?.pitch = pitch.coerceIn(0.5f, 2.0f)
    }
    
    /** Shutdown */
    fun shutdown() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.shutdown()
        tts = null
        ttsQueue.close()
        recognitionChannel.close()
    }
}

// ===== PORCUPINE WAKE WORD (Production Integration) =====
/*
class PorcupineWakeWordDetector(
    private val context: Context,
    private val accessKey: String,
    private val keywordPaths: List<String>,
    private val onHotword: (Int) -> Unit
) {
    private var porcupine: ai.picovoice.porcupine.Porcupine? = null
    private var audioRecord: android.media.AudioRecord? = null
    private var isRunning = false
    
    fun start() {
        if (isRunning) return
        
        try {
            val builder = ai.picovoice.porcupine.Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(keywordPaths.toTypedArray())
            
            porcupine = builder.build(context)
            
            val sampleRate = porcupine?.sampleRate ?: 16000
            val frameLength = porcupine?.frameLength ?: 512
            
            val bufferSize = android.media.AudioRecord.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )
            
            audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize.coerceAtLeast(frameLength * 2)
            )
            
            audioRecord?.startRecording()
            isRunning = true
            
            Thread { processAudio() }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun processAudio() {
        val frameLength = porcupine?.frameLength ?: 512
        val pcm = ShortArray(frameLength)
        
        while (isRunning) {
            val read = audioRecord?.read(pcm, 0, frameLength) ?: 0
            if (read >= frameLength) {
                val keywordIndex = porcupine?.process(pcm) ?: -1
                if (keywordIndex >= 0) {
                    onHotword(keywordIndex)
                }
            }
        }
    }
    
    fun stop() {
        isRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        porcupine?.delete()
        porcupine = null
    }
}
*/