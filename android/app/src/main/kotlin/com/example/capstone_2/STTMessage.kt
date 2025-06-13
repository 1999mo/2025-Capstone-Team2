package com.example.capstone_2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.os.Handler
import android.os.Looper

class STTMessage(private val activity: Activity) {
    var text: String = ""
    var onResult: ((String) -> Unit)? = null
    var onEnd: (() -> Unit)? = null

    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
    }

    private var endOfSpeechHandler: Handler? = null

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("STT", "onReadyForSpeech")
            }

            override fun onBeginningOfSpeech() {
                endOfSpeechHandler?.removeCallbacksAndMessages(null)
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                endOfSpeechHandler = Handler(Looper.getMainLooper())
                endOfSpeechHandler?.postDelayed({
                    speechRecognizer.stopListening()
                }, 3000)
            }

            override fun onError(error: Int) {
                Log.e("STT", "Error: $error")
                onEnd?.invoke()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    text = matches[0]
                    Log.d("STT", "onResults: $text")
                    onResult?.invoke(text)
                }
                onEnd?.invoke()
            }

            override fun onPartialResults(partialREsults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        speechRecognizer.startListening(recognizerIntent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun destroy() {
        speechRecognizer.destroy()
    }
}