package com.jarvis.assistant

import android.Manifest
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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
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

class JarvisVoiceService : Service() {

    private var recognizer: SpeechRecognizer? = null

    private lateinit var wakeWordEngine: WakeWordEngine

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private val handler = Handler(Looper.getMainLooper())

    private var commandListening = false
    private var wakeWordRunning = false
    private var lastWakeDetectionMs = 0L

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startJarvisForegroundService()
        setupWakeWord()
    }

    // ---------------------------------------------------------
    // VOZ - KOKORO / BM GEORGE
    // ---------------------------------------------------------

    private fun speak(text: String, onComplete: (() -> Unit)? = null) {
        JarvisKokoroTts.speak(this, text, onComplete)
    }

    // ---------------------------------------------------------
    // WAKE WORD
    // ---------------------------------------------------------

    private fun setupWakeWord() {
        try {
            val models = listOf(
                WakeWordModel(
                    name = "Hey Jarvis",
                    modelPath = "hey_jarvis_v0.1.onnx",
                    threshold = 0.08f
                )
            )

            wakeWordEngine = WakeWordEngine(
                context = this,
                models = models,
                detectionMode = DetectionMode.SINGLE_BEST,
                detectionCooldownMs = 2500L
            )

            scope.launch {
                try {
                    wakeWordEngine.detections.collect {
                        val now = android.os.SystemClock.elapsedRealtime()

                        // Ignore duplicate detections, including detections caused by
                        // residual audio from JARVIS itself.
                        if (commandListening || now - lastWakeDetectionMs < 4000L) {
                            return@collect
                        }

                        lastWakeDetectionMs = now
                        commandListening = true
                        stopWakeWord()

                        speak("Sim, senhor.") {
                            handler.post {
                                if (commandListening) {
                                    startListeningForCommand()
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("JARVIS", "Falha no fluxo do wake word", e)
                }
            }

            startWakeWord()
        } catch (e: Throwable) {
            android.util.Log.e("JARVIS", "Falha ao configurar wake word", e)
        }
    }

    private fun startWakeWord() {
        if (!::wakeWordEngine.isInitialized || wakeWordRunning) return

        try {
            wakeWordEngine.start()
            wakeWordRunning = true
        } catch (e: Throwable) {
            wakeWordRunning = false
            android.util.Log.e("JARVIS", "Falha ao iniciar wake word", e)
        }
    }

    private fun stopWakeWord() {
        if (!::wakeWordEngine.isInitialized || !wakeWordRunning) return

        try {
            wakeWordEngine.stop()
        } catch (e: Throwable) {
            android.util.Log.e("JARVIS", "Falha ao parar wake word", e)
        } finally {
            wakeWordRunning = false
        }
    }

    private fun restartWakeWord() {
        handler.postDelayed({
            if (!commandListening) {
                startWakeWord()
            }
        }, 1800L)
    }

    // ---------------------------------------------------------
    // RECONHECIMENTO DE COMANDO
    // ---------------------------------------------------------

    private fun startListeningForCommand() {
        if (!commandListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            finishCommand()
            return
        }

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onError(error: Int) {
                        finishCommand()
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()

                        if (text.isNullOrBlank()) {
                            finishCommand()
                            return
                        }

                        // Keep the wake word disabled until JARVIS finishes speaking.
                        speak(simpleCommand(text)) {
                            finishCommand()
                        }
                    }
                }
            )

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            recognizer?.startListening(intent)
        } catch (e: Throwable) {
            android.util.Log.e("JARVIS", "Falha ao iniciar reconhecimento", e)
            finishCommand()
        }
    }

    private fun finishCommand() {
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null

        commandListening = false
        restartWakeWord()
    }

    // ---------------------------------------------------------
    // COMANDOS
    // ---------------------------------------------------------

    private fun simpleCommand(text: String): String {

        val command = text
            .lowercase(Locale("pt", "BR"))
            .trim()

        return when {

            command.contains("que horas") ||
            command.contains("qual a hora") ->
                "Agora são ${
                    SimpleDateFormat(
                        "HH:mm",
                        Locale("pt", "BR")
                    ).format(Date())
                }."

            command.contains("quem é você") ||
            command.contains("quem voce é") ||
            command.contains("seu nome") ->
                "Sou JARVIS, seu assistente pessoal."

            command == "olá" ||
            command == "ola" ||
            command == "oi" ->
                "Olá. Como posso ajudá-lo?"

            command.contains("youtube") -> {
                openApp("com.google.android.youtube")
                "Abrindo o YouTube."
            }

            command.contains("spotify") -> {
                openSpotify()
                "Abrindo o Spotify."
            }

            command.contains("whatsapp") -> {
                openWhatsApp()
                "Abrindo o WhatsApp."
            }

            command.contains("câmera") ||
            command.contains("camera") -> {
                openCamera()
                "Abrindo a câmera."
            }

            command.contains("telefone") ||
            command.contains("ligação") ||
            command.contains("ligacao") -> {
                openPhone()
                "Abrindo o telefone."
            }

            // -------------------------------------------------
            // LANterna DESLIGADA
            // IMPORTANTE: fica ANTES dos comandos de ligar
            // -------------------------------------------------

            command.contains("desligue a lanterna") ||
            command.contains("desliga a lanterna") ||
            command.contains("desligar a lanterna") ||
            command.contains("apague a lanterna") ||
            command.contains("apagar a lanterna") ||
            command.contains("desative a lanterna") ||
            command.contains("desativar a lanterna") -> {

                if (setFlashlight(false)) {
                    "Lanterna desligada."
                } else {
                    "Não consegui acessar a lanterna."
                }
            }

            // -------------------------------------------------
            // LANterna LIGADA
            // -------------------------------------------------

            command.contains("ligue a lanterna") ||
            command.contains("liga a lanterna") ||
            command.contains("acenda a lanterna") ||
            command.contains("acender a lanterna") ||
            command.contains("ligar a lanterna") -> {

                if (setFlashlight(true)) {
                    "Lanterna ligada."
                } else {
                    "Não consegui acessar a lanterna."
                }
            }

            command.contains("aumente o volume") ||
            command.contains("aumentar o volume") ||
            command.contains("aumenta o volume") -> {

                changeVolume(AudioManager.ADJUST_RAISE)
                "Aumentando o volume."
            }

            command.contains("diminua o volume") ||
            command.contains("diminuir o volume") ||
            command.contains("diminui o volume") -> {

                changeVolume(AudioManager.ADJUST_LOWER)
                "Diminuindo o volume."
            }

            command.contains("silencie") ||
            command.contains("silenciar") ||
            command.contains("mudo") -> {

                muteVolume()
                "Volume silenciado."
            }

            command.contains("configurações") ||
            command.contains("configuracoes") ||
            command.contains("configuração") ||
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

            command.contains("play store") -> {

                openPlayStore()
                "Abrindo a Play Store."
            }

            command.contains("alarme") ||
            command.contains("despertador") ||
            command.contains("relógio") ||
            command.contains("relogio") -> {

                openAlarm()
                "Abrindo o relógio."
            }

            command.contains("tela inicial") -> {

                goHome()
                "Voltando para a tela inicial."
            }

            command.contains("bateria") -> {

                val batteryManager =
                    getSystemService(
                        BATTERY_SERVICE
                    ) as android.os.BatteryManager

                val battery =
                    batteryManager.getIntProperty(
                        android.os.BatteryManager
                            .BATTERY_PROPERTY_CAPACITY
                    )

                "A bateria está em $battery por cento."
            }

            command.contains("pesquisar") ||
            command.contains("pesquise") ||
            command.contains("procure no google") -> {

                searchGoogle(
                    extractSearchText(command)
                )

                "Pesquisando no Google."
            }

            else ->
                "Comando recebido: $text"
        }
    }

    // ---------------------------------------------------------
    // APLICATIVOS
    // ---------------------------------------------------------

    private fun openApp(packageName: String) {

        val intent =
            packageManager.getLaunchIntentForPackage(
                packageName
            ) ?: return

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        startActivity(intent)
    }

    private fun openSpotify() {

        val intent =
            packageManager.getLaunchIntentForPackage(
                "com.spotify.music"
            )

        if (intent != null) {

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            startActivity(intent)

        } else {

            openBrowserUrl(
                "https://open.spotify.com/"
            )
        }
    }

    private fun openWhatsApp() {

        val whatsapp =
            packageManager.getLaunchIntentForPackage(
                "com.whatsapp"
            )

        if (whatsapp != null) {

            whatsapp.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            startActivity(whatsapp)
            return
        }

        val business =
            packageManager.getLaunchIntentForPackage(
                "com.whatsapp.w4b"
            )

        if (business != null) {

            business.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            startActivity(business)
            return
        }

        openBrowserUrl(
            "https://web.whatsapp.com/"
        )
    }

    private fun openPhone() {

        val intent = Intent(
            Intent.ACTION_DIAL
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        if (
            intent.resolveActivity(packageManager) != null
        ) {
            startActivity(intent)
        }
    }

    private fun openCamera() {

        val intent = Intent(
            MediaStore.ACTION_IMAGE_CAPTURE
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        if (
            intent.resolveActivity(packageManager) != null
        ) {
            startActivity(intent)
        }
    }

    // ---------------------------------------------------------
    // LANTERNA
    // ---------------------------------------------------------

    private fun setFlashlight(
        enabled: Boolean
    ): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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
                        cameraManager
                            .getCameraCharacteristics(id)

                    val hasFlash =
                        characteristics.get(
                            CameraCharacteristics
                                .FLASH_INFO_AVAILABLE
                        ) == true

                    val facing =
                        characteristics.get(
                            CameraCharacteristics
                                .LENS_FACING
                        )

                    hasFlash &&
                    facing ==
                    CameraCharacteristics
                        .LENS_FACING_BACK
                }

            if (cameraId == null) {
                false
            } else {

                cameraManager.setTorchMode(
                    cameraId,
                    enabled
                )

                true
            }

        } catch (_: Exception) {
            false
        }
    }

    // ---------------------------------------------------------
    // VOLUME
    // ---------------------------------------------------------

    private fun changeVolume(direction: Int) {

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

    // ---------------------------------------------------------
    // PESQUISA
    // ---------------------------------------------------------

    private fun searchGoogle(query: String) {

        val finalQuery =
            if (query.isBlank()) "Google" else query

        openBrowserUrl(
            "https://www.google.com/search?q=" +
                    Uri.encode(finalQuery)
        )
    }

    private fun extractSearchText(
        text: String
    ): String {

        return text
            .replace("pesquise", "")
            .replace("pesquisar", "")
            .replace("procure no google", "")
            .trim()
    }

    // ---------------------------------------------------------
    // SISTEMA
    // ---------------------------------------------------------

    private fun openPlayStore() {

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "market://details?id=com.android.vending"
            )
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            openBrowserUrl(
                "https://play.google.com/store"
            )
        }
    }

    private fun openAlarm() {

        val intent = Intent(
            AlarmClock.ACTION_SHOW_ALARMS
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        if (
            intent.resolveActivity(packageManager) != null
        ) {
            startActivity(intent)
        }
    }

    private fun goHome() {

        val intent = Intent(
            Intent.ACTION_MAIN
        ).apply {

            addCategory(Intent.CATEGORY_HOME)

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        startActivity(intent)
    }

    private fun openSettings() {

        val intent = Intent(
            Settings.ACTION_SETTINGS
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        startActivity(intent)
    }

    private fun openBrowser() {
        openBrowserUrl(
            "https://www.google.com"
        )
    }

    private fun openBrowserUrl(
        url: String
    ) {

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        if (
            intent.resolveActivity(packageManager) != null
        ) {
            startActivity(intent)
        }
    }

    // ---------------------------------------------------------
    // SERVIÇO EM SEGUNDO PLANO
    // ---------------------------------------------------------

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                "jarvis_voice",
                "JARVIS Voz",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun startJarvisForegroundService() {

        val notification =
            Notification.Builder(
                this,
                "jarvis_voice"
            )
                .setContentTitle("JARVIS")
                .setContentText(
                    "Diga \"Hey Jarvis\" para ativar"
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .build()

        startForeground(
            42,
            notification
        )
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null

        stopWakeWord()

        scope.cancel()

        try {
            JarvisKokoroTts.release()
        } catch (_: Throwable) {
        }

        super.onDestroy()
    }

}
