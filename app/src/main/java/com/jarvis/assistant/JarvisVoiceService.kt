package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
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
                            simpleCommand(text)

                        if (response.isNotBlank()) {
                            speak(response)
                        }
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

            "que horas" in l ||
            "qual a hora" in l ->
                "Agora são " +
                        SimpleDateFormat(
                            "HH:mm",
                            Locale("pt", "BR")
                        ).format(Date())

            "quem é você" in l ||
            "quem voce é" in l ||
            "seu nome" in l ->
                "Sou JARVIS."

            "olá" in l ||
            "ola" in l ||
            "oi" in l ->
                "Olá. Como posso ajudá-lo?"

            "youtube" in l -> {

                openAppByName("youtube")

                "Abrindo o YouTube."
            }

            "spotify" in l -> {

                openAppByName("spotify")

                "Abrindo o Spotify."
            }

            "whatsapp" in l -> {

                openAppByName("whatsapp")

                "Abrindo o WhatsApp."
            }

            "configurações" in l ||
            "configuração" in l ||
            "configuracoes" in l ||
            "configuracao" in l -> {

                openSettings()

                "Abrindo as configurações."
            }

            "navegador" in l ||
            "internet" in l ||
            "google chrome" in l ||
            "chrome" in l -> {

                openBrowser()

                "Abrindo o navegador."
            }

            "telefone" in l ||
            "ligações" in l ||
            "ligação" in l ||
            "ligacoes" in l ||
            "ligacao" in l -> {

                openPhone()

                "Abrindo o telefone."
            }

            "câmera" in l ||
            "camera" in l -> {

                openCamera()

                "Abrindo a câmera."
            }

            "ligue a lanterna" in l ||
            "liga a lanterna" in l ||
            "acenda a lanterna" in l ||
            "acender a lanterna" in l ||
            "ligar a lanterna" in l -> {

                setFlashlight(true)

                "Ligando a lanterna."
            }

            "desligue a lanterna" in l ||
            "desliga a lanterna" in l ||
            "apague a lanterna" in l ||
            "apagar a lanterna" in l ||
            "desligar a lanterna" in l -> {

                setFlashlight(false)

                "Desligando a lanterna."
            }

            "aumente o volume" in l ||
            "aumentar o volume" in l ||
            "aumenta o volume" in l ||
            "mais volume" in l -> {

                changeVolume(
                    AudioManager.ADJUST_RAISE
                )

                "Aumentando o volume."
            }

            "diminua o volume" in l ||
            "diminuir o volume" in l ||
            "diminui o volume" in l ||
            "menos volume" in l -> {

                changeVolume(
                    AudioManager.ADJUST_LOWER
                )

                "Diminuindo o volume."
            }

            "silencie o volume" in l ||
            "silenciar o volume" in l ||
            "mudo" in l ||
            "modo silencioso" in l -> {

                muteVolume()

                "Volume silenciado."
            }

            "pesquise" in l ||
            "pesquisar" in l ||
            "procure no google" in l ||
            "pesquisa no google" in l -> {

                searchGoogle(
                    extractSearchText(l)
                )

                "Pesquisando no Google."
            }

            "play store" in l ||
            "loja de aplicativos" in l -> {

                openPlayStore()

                "Abrindo a Play Store."
            }

            "alarme" in l ||
            "despertador" in l ||
            "relógio" in l ||
            "relogio" in l -> {

                openAlarm()

                "Abrindo o relógio."
            }

            "tela inicial" in l ||
            "voltar para o início" in l ||
            "voltar para o inicio" in l ||
            "ir para o início" in l ||
            "ir para o inicio" in l -> {

                goHome()

                "Voltando para a tela inicial."
            }

            else ->
                "Comando recebido: $text"
        }
    }

    private fun openAppByName(
        appName: String
    ) {

        val packages =
            when (appName) {

                "youtube" ->
                    listOf(
                        "com.google.android.youtube"
                    )

                "spotify" ->
                    listOf(
                        "com.spotify.music"
                    )

                "whatsapp" ->
                    listOf(
                        "com.whatsapp",
                        "com.whatsapp.w4b"
                    )

                else ->
                    emptyList()
            }

        for (packageName in packages) {

            val intent =
                packageManager.getLaunchIntentForPackage(
                    packageName
                )

            if (intent != null) {

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                startActivity(intent)

                return
            }
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
    ) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        try {

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

                    val lensFacing =
                        characteristics.get(
                            CameraCharacteristics.LENS_FACING
                        )

                    hasFlash &&
                            lensFacing ==
                            CameraCharacteristics.LENS_FACING_BACK
                }

            if (cameraId != null) {

                cameraManager.setTorchMode(
                    cameraId,
                    enabled
                )
            }

        } catch (_: Exception) {
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

        val finalQuery =
            if (query.isBlank()) {
                "Google"
            } else {
                query
            }

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://www.google.com/search?q=" +
                            Uri.encode(finalQuery)
                )
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(intent)
    }

    private fun extractSearchText(
        text: String
    ): String {

        return text
            .replace(
                "pesquise",
                ""
            )
            .replace(
                "pesquisar",
                ""
            )
            .replace(
                "procure no google",
                ""
            )
            .replace(
                "pesquisa no google",
                ""
            )
            .trim()
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
