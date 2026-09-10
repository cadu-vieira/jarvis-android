package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.AlarmClock
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
                            executeCommand(text)

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

    private fun executeCommand(
        text: String
    ): String {

        val command =
            normalizeText(text)

        return when {

            command.contains("que horas") ||
            command.contains("qual a hora") ||
            command.contains("horas sao") ||
            command.contains("horas são") -> {

                "Agora são " +
                        SimpleDateFormat(
                            "HH:mm",
                            Locale("pt", "BR")
                        ).format(Date())
            }

            command.contains("quem e voce") ||
            command.contains("quem voce e") ||
            command.contains("seu nome") -> {

                "Sou JARVIS."
            }

            command == "oi" ||
            command == "ola" ||
            command.startsWith("ola ") ||
            command.startsWith("oi ") -> {

                "Olá. Como posso ajudá-lo?"
            }

            command.contains("youtube") -> {

                if (openApp(
                        "com.google.android.youtube"
                    )
                ) {
                    "Abrindo o YouTube."
                } else {
                    "O YouTube não está disponível."
                }
            }

            command.contains("spotify") -> {

                if (openApp(
                        "com.spotify.music"
                    )
                ) {
                    "Abrindo o Spotify."
                } else {
                    "Não encontrei o Spotify instalado."
                }
            }

            command.contains("whatsapp") -> {

                if (
                    openApp("com.whatsapp") ||
                    openApp("com.whatsapp.w4b")
                ) {
                    "Abrindo o WhatsApp."
                } else {
                    "Não encontrei o WhatsApp instalado."
                }
            }

            command.contains("configuracao") -> {

                openSettings()

                "Abrindo as configurações."
            }

            command.contains("navegador") ||
            command.contains("internet") ||
            command.contains("chrome") -> {

                openBrowser()

                "Abrindo o navegador."
            }

            command.contains("telefone") ||
            command.contains("ligacao") ||
            command.contains("ligaçoes") ||
            command.contains("ligacoes") -> {

                openPhone()

                "Abrindo o telefone."
            }

            command.contains("camera") -> {

                openCamera()

                "Abrindo a câmera."
            }

            command.contains("lanterna") &&
            (
                command.contains("ligar") ||
                command.contains("liga") ||
                command.contains("acender") ||
                command.contains("acenda") ||
                command.contains("ativa") ||
                command.contains("ativar")
            ) -> {

                if (setFlashlight(true)) {
                    "Lanterna ligada."
                } else {
                    "Não consegui controlar a lanterna."
                }
            }

            command.contains("lanterna") &&
            (
                command.contains("desligar") ||
                command.contains("desliga") ||
                command.contains("apagar") ||
                command.contains("apaga") ||
                command.contains("desativa") ||
                command.contains("desativar")
            ) -> {

                if (setFlashlight(false)) {
                    "Lanterna desligada."
                } else {
                    "Não consegui controlar a lanterna."
                }
            }

            command.contains("aumentar volume") ||
            command.contains("aumente o volume") ||
            command.contains("aumenta o volume") ||
            command.contains("mais volume") -> {

                changeVolume(
                    AudioManager.ADJUST_RAISE
                )

                "Aumentando o volume."
            }

            command.contains("diminuir volume") ||
            command.contains("diminua o volume") ||
            command.contains("diminui o volume") ||
            command.contains("menos volume") -> {

                changeVolume(
                    AudioManager.ADJUST_LOWER
                )

                "Diminuindo o volume."
            }

            command.contains("silenciar volume") ||
            command.contains("silencie o volume") ||
            command.contains("volume mudo") ||
            command == "mudo" -> {

                muteVolume()

                "Volume silenciado."
            }

            command.contains("play store") ||
            command.contains("loja de aplicativos") -> {

                openPlayStore()

                "Abrindo a Play Store."
            }

            command.contains("alarme") ||
            command.contains("despertador") ||
            command.contains("relogio") -> {

                openAlarm()

                "Abrindo o relógio."
            }

            command.contains("tela inicial") ||
            command.contains("ir para inicio") ||
            command.contains("voltar para inicio") -> {

                goHome()

                "Voltando para a tela inicial."
            }

            command.startsWith("pesquisar ") ||
            command.startsWith("pesquise ") ||
            command.startsWith("procure ") ||
            command.startsWith("buscar ") -> {

                val search =
                    command
                        .replaceFirst(
                            Regex(
                                "^(pesquisar|pesquise|procure|buscar)\\s+"
                            ),
                            ""
                        )
                        .trim()

                if (search.isNotBlank()) {

                    searchGoogle(search)

                    "Pesquisando por $search."
                } else {
                    "O que você quer pesquisar?"
                }
            }

            else -> {

                "Não entendi o comando."
            }
        }
    }

    private fun normalizeText(
        text: String
    ): String {

        return text
            .lowercase(Locale("pt", "BR"))
            .replace("á", "a")
            .replace("à", "a")
            .replace("ã", "a")
            .replace("â", "a")
            .replace("é", "e")
            .replace("ê", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ô", "o")
            .replace("õ", "o")
            .replace("ú", "u")
            .replace("ç", "c")
            .trim()
    }

    private fun openApp(
        packageName: String
    ): Boolean {

        val intent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        return if (intent != null) {

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            startActivity(intent)

            true

        } else {
            false
        }
    }

    private fun openPhone() {

        val intent =
            Intent(
                Intent.ACTION_DIAL
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        if (
            intent.resolveActivity(
                packageManager
            ) != null
        ) {
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

        if (
            intent.resolveActivity(
                packageManager
            ) != null
        ) {
            startActivity(intent)
        }
    }

    private fun setFlashlight(
        enabled: Boolean
    ): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        return try {

            val cameraManager =
                getSystemService(
                    CAMERA_SERVICE
                ) as CameraManager

            val cameraId =
                cameraManager.cameraIdList.firstOrNull { id ->

                    val characteristics =
                        cameraManager.getCameraCharacteristics(
                            id
                        )

                    val hasFlash =
                        characteristics.get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) == true

                    val facing =
                        characteristics.get(
                            CameraCharacteristics.LENS_FACING
                        )

                    hasFlash &&
                            facing ==
                            CameraCharacteristics.LENS_FACING_BACK
                }

            if (cameraId != null) {

                cameraManager.setTorchMode(
                    cameraId,
                    enabled
                )

                true

            } else {
                false
            }

        } catch (_: Exception) {
            false
        }
    }

    private fun changeVolume(
        direction: Int
    ) {

        val audioManager =
            getSystemService(
                AUDIO_SERVICE
            ) as AudioManager

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun muteVolume() {

        val audioManager =
            getSystemService(
                AUDIO_SERVICE
            ) as AudioManager

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_MUTE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun searchGoogle(
        query: String
    ) {

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://www.google.com/search?q=" +
                            Uri.encode(query)
                )
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(intent)
    }

    private fun openPlayStore() {

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "market://details?id=com.android.vending"
                )
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        try {

            startActivity(intent)

        } catch (_: Exception) {

            val browserIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://play.google.com/store"
                    )
                )

            browserIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            startActivity(browserIntent)
        }
    }

    private fun openAlarm() {

        val intent =
            Intent(
                AlarmClock.ACTION_SHOW_ALARMS
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        if (
            intent.resolveActivity(
                packageManager
            ) != null
        ) {
            startActivity(intent)
        }
    }

    private fun goHome() {

        val intent =
            Intent(
                Intent.ACTION_MAIN
            ).apply {

                addCategory(
                    Intent.CATEGORY_HOME
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        startActivity(intent)
    }

    private fun openSettings() {

        val intent =
            Intent(
                Settings.ACTION_SETTINGS
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(intent)
    }

    private fun openBrowser() {

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://www.google.com"
                )
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setFlashlight(false)
        }

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
