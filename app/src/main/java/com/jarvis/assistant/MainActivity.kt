package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var conversation: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText

    private var recognizer: SpeechRecognizer? = null

    private val microphonePermissionCode = 100
    private val cameraPermissionCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        conversation = findViewById(R.id.conversation)
        status = findViewById(R.id.status)
        input = findViewById(R.id.input)

        findViewById<Button>(R.id.send).setOnClickListener {

            val text =
                input.text.toString().trim()

            if (text.isNotEmpty()) {

                input.setText("")

                processCommand(text)
            }
        }

        findViewById<Button>(R.id.mic).setOnClickListener {
            listenOnce()
        }

        findViewById<Button>(R.id.backgroundVoice).setOnClickListener {
            startBackgroundVoice()
        }

        addLine(
            "JARVIS",
            "Sistema inicializado. Aguardando \"Hey Jarvis\"."
        )

        requestRequiredPermissions()

        // O Wake Word deve ficar ativo sem precisar apertar nenhum botão.
        // Se o microfone já estiver autorizado, iniciamos o serviço imediatamente.
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startBackgroundVoice()
        }
    }

    private fun startBackgroundVoice() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestRequiredPermissions()
            status.text = "Permita o microfone para ativar o Hey Jarvis."
            return
        }

        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, JarvisVoiceService::class.java)
            )
            status.text = "Hey Jarvis ativo."
        } catch (error: Throwable) {
            status.text = "Não foi possível ativar a voz."
            android.util.Log.e("JARVIS", "Falha ao iniciar serviço de voz", error)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != microphonePermissionCode) return

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startBackgroundVoice()
        } else {
            status.text = "O microfone é necessário para o Hey Jarvis."
        }
    }

    private fun requestRequiredPermissions() {

        val permissions =
            mutableListOf<String>()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(
                Manifest.permission.RECORD_AUDIO
            )
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(
                Manifest.permission.CAMERA
            )
        }

        if (permissions.isNotEmpty()) {

            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                microphonePermissionCode
            )
        }
    }

    private fun processCommand(
        text: String
    ) {

        addLine(
            "VOCÊ",
            text
        )

        val lower =
            text.lowercase(
                Locale("pt", "BR")
            )

        val response: String

        when {

            "que horas" in lower ||
            "qual a hora" in lower ||
            lower == "horas" -> {

                response =
                    "Agora são " +
                            SimpleDateFormat(
                                "HH:mm",
                                Locale("pt", "BR")
                            ).format(Date())
            }

            "quem é você" in lower ||
            "quem voce é" in lower ||
            "seu nome" in lower -> {

                response =
                    "Sou JARVIS, seu assistente pessoal."
            }

            "youtube" in lower -> {

                openApp(
                    "com.google.android.youtube"
                )

                response =
                    "Abrindo o YouTube."
            }

            "spotify" in lower -> {

                openSpotify()

                response =
                    "Abrindo o Spotify."
            }

            "whatsapp" in lower -> {

                openWhatsApp()

                response =
                    "Abrindo o WhatsApp."
            }

            "configurações" in lower ||
            "configuracoes" in lower ||
            "configuração" in lower ||
            "configuracao" in lower -> {

                startActivity(
                    Intent(
                        Settings.ACTION_SETTINGS
                    )
                )

                response =
                    "Abrindo as configurações."
            }

            "navegador" in lower ||
            "internet" in lower ||
            "chrome" in lower -> {

                openBrowser()

                response =
                    "Abrindo o navegador."
            }

            "telefone" in lower ||
            "ligação" in lower ||
            "ligacao" in lower -> {

                openPhone()

                response =
                    "Abrindo o telefone."
            }

            "câmera" in lower ||
            "camera" in lower -> {

                openCamera()

                response =
                    "Abrindo a câmera."
            }

            // -------------------------------------------------
            // LANterna DESLIGADA
            // IMPORTANTE: fica ANTES dos comandos de ligar.
            // -------------------------------------------------

            "desligue a lanterna" in lower ||
            "desliga a lanterna" in lower ||
            "desligar a lanterna" in lower ||
            "apague a lanterna" in lower ||
            "apagar a lanterna" in lower ||
            "desative a lanterna" in lower ||
            "desativar a lanterna" in lower -> {

                setFlashlight(false)

                response =
                    "Lanterna desligada."
            }

            // -------------------------------------------------
            // LANterna LIGADA
            // -------------------------------------------------

            "ligue a lanterna" in lower ||
            "liga a lanterna" in lower ||
            "acenda a lanterna" in lower ||
            "acender a lanterna" in lower ||
            "ligar a lanterna" in lower -> {

                setFlashlight(true)

                response =
                    "Lanterna ligada."
            }

            "aumente o volume" in lower ||
            "aumentar o volume" in lower ||
            "aumenta o volume" in lower -> {

                changeVolume(
                    AudioManager.ADJUST_RAISE
                )

                response =
                    "Aumentando o volume."
            }

            "diminua o volume" in lower ||
            "diminuir o volume" in lower ||
            "diminui o volume" in lower -> {

                changeVolume(
                    AudioManager.ADJUST_LOWER
                )

                response =
                    "Diminuindo o volume."
            }

            "silencie" in lower ||
            "silenciar" in lower ||
            "mudo" in lower -> {

                muteVolume()

                response =
                    "Volume silenciado."
            }

            "play store" in lower ||
            "loja de aplicativos" in lower -> {

                openPlayStore()

                response =
                    "Abrindo a Play Store."
            }

            "alarme" in lower ||
            "despertador" in lower ||
            "relógio" in lower ||
            "relogio" in lower -> {

                openAlarm()

                response =
                    "Abrindo o relógio."
            }

            "tela inicial" in lower -> {

                goHome()

                response =
                    "Voltando para a tela inicial."
            }

            "bateria" in lower -> {

                val batteryManager =
                    getSystemService(
                        BATTERY_SERVICE
                    ) as android.os.BatteryManager

                val battery =
                    batteryManager.getIntProperty(
                        android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
                    )

                response =
                    "A bateria está em $battery por cento."
            }

            "pesquisar" in lower ||
            "pesquise" in lower ||
            "procure no google" in lower -> {

                searchGoogle(
                    extractSearchText(lower)
                )

                response =
                    "Pesquisando no Google."
            }

            else -> {

                response =
                    "Entendi: $text."
            }
        }

        addLine(
            "JARVIS",
            response
        )

        speak(response)
    }

    private fun openApp(
        packageName: String
    ) {

        val intent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        if (intent != null) {

            startActivity(intent)
        }
    }

    private fun openSpotify() {

        val intent =
            packageManager.getLaunchIntentForPackage(
                "com.spotify.music"
            )

        if (intent != null) {

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

        val whatsappBusiness =
            packageManager.getLaunchIntentForPackage(
                "com.whatsapp.w4b"
            )

        when {

            whatsapp != null ->
                startActivity(whatsapp)

            whatsappBusiness != null ->
                startActivity(whatsappBusiness)

            else ->
                openBrowserUrl(
                    "https://web.whatsapp.com/"
                )
        }
    }

    private fun openPhone() {

        val intent =
            Intent(
                Intent.ACTION_DIAL
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

        if (
            android.os.Build.VERSION.SDK_INT <
            android.os.Build.VERSION_CODES.M
        ) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA
                ),
                cameraPermissionCode
            )

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
                        cameraManager
                            .getCameraCharacteristics(id)

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

    private fun openBrowser() {

        openBrowserUrl(
            "https://www.google.com"
        )
    }

    private fun openBrowserUrl(
        url: String
    ) {

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )

        if (
            intent.resolveActivity(
                packageManager
            ) != null
        ) {
            startActivity(intent)
        }
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

    private fun openPlayStore() {

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "market://details?id=com.android.vending"
                )
            )

        try {

            startActivity(intent)

        } catch (_: Exception) {

            openBrowserUrl(
                "https://play.google.com/store"
            )
        }
    }

    private fun openAlarm() {

        val intent =
            Intent(
                AlarmClock.ACTION_SHOW_ALARMS
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
            }

        startActivity(intent)
    }

    private fun addLine(
        who: String,
        text: String
    ) {

        conversation.append(
            "\n$who: $text\n"
        )
    }

    private fun speak(text: String) {
        JarvisKokoroTts.speak(this, text)
    }

    private fun listenOnce() {

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                this
            )
        ) {

            status.text =
                "Reconhecimento de voz indisponível."

            return
        }

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer
                .createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    status.text = "Ouvindo..."
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
                    status.text = "Processando..."
                }

                override fun onError(
                    error: Int
                ) {

                    status.text =
                        "Não entendi. Tente novamente."
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    val list =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val text =
                        list?.firstOrNull()

                    if (!text.isNullOrBlank()) {

                        processCommand(text)
                    }

                    status.text =
                        "Sistema pronto."
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
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

    override fun onDestroy() {

        recognizer?.destroy()
        JarvisKokoroTts.release()

        super.onDestroy()
    }
}
