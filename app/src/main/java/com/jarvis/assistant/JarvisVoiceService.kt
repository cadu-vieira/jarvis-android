package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech

import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel

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

    private val handler =
        Handler(Looper.getMainLooper())

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

        setupWakeWord()
    }

    private fun setupWakeWord() {

        val models = listOf(
            WakeWordModel(
                name = "Hey Jarvis",
                modelPath = "hey_jarvis_v0.1.onnx",
                threshold = 0.08f
            )
        )

        wakeWordEngine =
            WakeWordEngine(
                context = this,
                models = models,
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
                    1200L
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

                override fun onReadyForSpeech(
                    params: android.os.Bundle?
                ) {
                }

                override fun onBeginningOfSpeech() {
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
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

                override fun onError(
                    error: Int
                ) {

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
                        1500L
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
                    wakeWordEngine.start()
                }
            },
            1000L
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

    private fun simpleCommand(
        text: String
    ): String {

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

            "youtube" in l -> {

                openApp("com.google.android.youtube")

                "Abrindo o YouTube."
            }

            "configurações" in l ||
            "configuração" in l -> {

                openSettings()

                "Abrindo as configurações."
            }

            "navegador" in l ||
            "internet" in l -> {

                openBrowser()

                "Abrindo o navegador."
            }

            "whatsapp" in l -> {

                openApp("com.whatsapp")

                "Abrindo o WhatsApp."
            }

            "spotify" in l -> {

                openApp("com.spotify.music")

                "Abrindo o Spotify."
            }

            "telefone" in l ||
            "ligações" in l ||
            "ligação" in l -> {

                openApp("com.google.android.dialer")

                "Abrindo o telefone."
            }

            "câmera" in l ||
            "camera" in l -> {

                openCamera()

                "Abrindo a câmera."
            }

            else ->
                "Comando recebido: $text"
        }
    }

    private fun openApp(
        packageName: String
    ) {

        val intent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        if (intent != null) {

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            startActivity(intent)
        }
    }

    private fun openCamera() {

        val intent =
            Intent(
                android.provider.MediaStore.ACTION_IMAGE_CAPTURE
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(intent)
    }

    private fun openSettings() {

        val intent =
            Intent(Settings.ACTION_SETTINGS)

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(intent)
    }

    private fun openBrowser() {

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com")
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(intent)
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

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    override fun onInit(
        status: Int
    ) {

        if (status == TextToSpeech.SUCCESS) {

            tts.language =
                Locale("pt", "BR")
        }
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        recognizer?.destroy()
        recognizer = null

        if (::wakeWordEngine.isInitialized) {
            wakeWordEngine.release()
        }

        serviceScope.cancel()

        if (::tts.isInitialized) {
            tts.shutdown()
        }

        super.onDestroy()
    }
}
