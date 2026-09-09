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

    private var isWaitingForCommand = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        tts = TextToSpeech(this, this)

        createChannel()

        val notification = Notification.Builder(this, "jarvis_voice")
            .setContentTitle("JARVIS")
            .setContentText("Diga \"Jarvis\" para ativar")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(42, notification)

        startListeningForWakeWord()
    }

    private fun startListeningForWakeWord() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        recognizer?.destroy()

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : android.speech.RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}

                override fun onError(error: Int) {
                    restartListening(1000)
                }

                override fun onResults(results: Bundle?) {

                    val resultsList =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val text = resultsList
                        ?.firstOrNull()
                        ?.lowercase(Locale("pt", "BR"))
                        ?.trim()

                    if (!text.isNullOrBlank()) {

                        if (isWaitingForCommand) {

                            val response = simpleCommand(text)

                            speak(response)

                            isWaitingForCommand = false

                            restartListening(1800)

                        } else {

                            if (
                                text.contains("jarvis") ||
                                text.contains("jarvis")
                            ) {

                                isWaitingForCommand = true

                                speak("Sim, senhor.")

                                restartListening(1200)

                            } else {

                                restartListening(500)

                            }
                        }

                    } else {

                        restartListening(700)
                    }
                }
            }
        )

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "pt-BR"
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )
        }

        recognizer?.startListening(intent)
    }

    private fun restartListening(delay: Long) {

        handler.postDelayed({

            if (!isWaitingForCommand) {
                startListeningForWakeWord()
            } else {
                startListeningForCommand()
            }

        }, delay)
    }

    private fun startListeningForCommand() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : android.speech.RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}

                override fun onError(error: Int) {

                    isWaitingForCommand = false

                    restartListening(1000)
                }

                override fun onResults(results: Bundle?) {

                    val text =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull()

                    if (!text.isNullOrBlank()) {

                        val response =
                            simpleCommand(text)

                        speak(response)
                    }

                    isWaitingForCommand = false

                    restartListening(1500)
                }
            }
        )

        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "pt-BR"
                )
            }

        recognizer?.startListening(intent)
    }

    private fun speak(text: String) {

        if (::tts.isInitialized) {

            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis"
            )
        }
    }

    private fun simpleCommand(text: String): String {

        val l =
            text.lowercase(Locale("pt", "BR"))

        return when {

            "que horas" in l ->
                "Agora são " +
                java.text.SimpleDateFormat(
                    "HH:mm",
                    Locale("pt", "BR")
                ).format(Date())

            "quem é você" in l ||
            "seu nome" in l ->
                "Sou JARVIS."

            "olá" in l ||
            "oi" in l ->
                "Olá. Como posso ajudá-lo?"

            else ->
                "Comando recebido: $text"
        }
    }

    private fun createChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    "jarvis_voice",
                    "JARVIS Voz",
                    NotificationManager.IMPORTANCE_LOW
                )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            tts.language =
                Locale("pt", "BR")
        }
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        recognizer?.destroy()

        if (::tts.isInitialized) {
            tts.shutdown()
        }

        super.onDestroy()
    }
}
