package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech

import com.rementia.openwakeword.lib.DetectionMode
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.WakeWordModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech

    private lateinit var wakeWordEngine: WakeWordEngine

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val handler = Handler(Looper.getMainLooper())

    private var commandListening = false

    override fun onCreate() {
        super.onCreate()

        tts = TextToSpeech(this, this)

        createChannel()

        val notification =
            Notification.Builder(this, "jarvis_voice")
                .setContentTitle("JARVIS")
                .setContentText("Diga \"Hey Jarvis\" para ativar")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()

        startForeground(42, notification)

        startWakeWord()
    }

    private fun startWakeWord() {

        try {
            wakeWordEngine.release()
        } catch (_: Exception) {
        }

        wakeWordEngine = WakeWordEngine(
            context = this,
            models = listOf(
                WakeWordModel(
                    name = "Hey Jarvis",
                    modelPath = "hey_jarvis_v0.1.onnx",
                    threshold = 0.08f
                )
            ),
            detectionMode = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = 2000L
        )

        serviceScope.launch {

            wakeWordEngine.detections.collect {

                if (commandListening) {
                    return@collect
                }

                commandListening = true

                wakeWordEngine.stop()

                speak("Sim, senhor.")

                handler.postDelayed(
                    {
                        startListeningForCommand()
                    },
                    1200
                )
            }
        }

        wakeWordEngine.start()
    }

    private fun startListeningForCommand() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            commandListening = false
            restartWakeWord()
            return
        }

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: android.os.Bundle?) {
                }

                override fun onBeginningOfSpeech() {
                }

                override fun onRmsChanged(rmsdB: Float) {
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                }

                override fun onEndOfSpeech() {
                }

                override fun onPartialResults(
                    partialResults: android.os.Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: android.os.Bundle?
                ) {
                }

                override fun onError(error: Int) {

                    recognizer?.destroy()
                    recognizer = null

                    commandListening = false

                    restartWakeWord()
                }

                override fun onResults(
                    results: android.os.Bundle?
                ) {

                    val text =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()

                    if (!text.isNullOrBlank()) {

                        val response =
                            simpleCommand(text)

                        speak(response)
                    }

                    recognizer?.destroy()
                    recognizer = null

                    commandListening = false

                    handler.postDelayed(
                        {
                            restartWakeWord()
                        },
                        1500
                    )
                }
            }
        )

        val intent =
            Intent(
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

    private fun restartWakeWord() {

        handler.postDelayed(
            {
                if (!commandListening) {
                    startWakeWord()
                }
            },
            1000
        )
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
            text.lowercase(
                Locale("pt", "BR")
            )

        return when {

            "que horas" in l ->
                "Agora são " +
                        SimpleDateFormat(
                            "HH:mm",
                            Locale("pt", "BR")
                        ).format(Date())

            "quem é você" in l ||
            "seu nome" in l ->
                "Sou JARVIS."

            "olá" in l ||
            "oi" in l ->
                "Olá. Como posso ajudá-lo?"

            "youtube" in l ->
                "Abrindo o YouTube."

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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            tts.language =
                Locale("pt", "BR")
        }
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        recognizer?.destroy()
        recognizer = null

        try {
            wakeWordEngine.release()
        } catch (_: Exception) {
        }

        serviceScope.cancel()

        if (::tts.isInitialized) {
            tts.shutdown()
        }

        super.onDestroy()
    }
}
