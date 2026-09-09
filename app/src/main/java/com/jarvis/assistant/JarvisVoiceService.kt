package com.jarvis.assistant

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.*

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createChannel()
        val notification = Notification.Builder(this, "jarvis_voice")
            .setContentTitle("JARVIS")
            .setContentText("Assistente de voz ativo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        startForeground(42, notification)
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 1200) }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    val response = simpleCommand(text)
                    tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
                }
                Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 700)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        }
        recognizer?.startListening(i)
    }

    private fun simpleCommand(text: String): String {
        val l = text.lowercase(Locale("pt", "BR"))
        return when {
            "que horas" in l -> "Agora são " + java.text.SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())
            "quem é você" in l || "seu nome" in l -> "Sou JARVIS."
            else -> "Comando recebido: $text"
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("jarvis_voice", "JARVIS Voz", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?) = null
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.language = Locale("pt", "BR") }
    override fun onDestroy() {
        recognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
