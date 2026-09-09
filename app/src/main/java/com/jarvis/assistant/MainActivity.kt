package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var conversation: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        conversation = findViewById(R.id.conversation)
        status = findViewById(R.id.status)
        input = findViewById(R.id.input)

        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.send).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                input.setText("")
                processCommand(text)
            }
        }

        findViewById<Button>(R.id.mic).setOnClickListener { listenOnce() }

        findViewById<Button>(R.id.backgroundVoice).setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            } else {
                val i = Intent(this, JarvisVoiceService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(this, i)
                status.text = "Escuta em segundo plano ativada."
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        addLine("JARVIS", "Sistema inicializado. Aguardando comando.")
    }

    private fun processCommand(text: String) {
        addLine("VOCÊ", text)
        val lower = text.lowercase(Locale("pt", "BR"))
        val response = when {
            "que horas" in lower || lower == "horas" ->
                "Agora são " + SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())
            "quem é você" in lower || "seu nome" in lower ->
                "Sou JARVIS, seu assistente pessoal."
            "youtube" in lower -> {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com")))
                "Abrindo o YouTube."
            }
            "google" in lower || "pesquisar" in lower ->
                "Posso abrir a pesquisa para você."
            "configurações" in lower || "configuracoes" in lower -> {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                "Abrindo as configurações."
            }
            "bateria" in lower -> {
                val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                "A bateria está em ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)} por cento."
            }
            else ->
                "Entendi: $text. O módulo de IA pode ser conectado ao núcleo para responder e executar ferramentas."
        }
        addLine("JARVIS", response)
        speak(response)
    }

    private fun addLine(who: String, text: String) {
        conversation.append("\n$who: $text\n")
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun listenOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Reconhecimento de voz indisponível."
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Ouvindo..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Processando..." }
            override fun onError(error: Int) { status.text = "Não entendi. Tente novamente." }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()
                if (!text.isNullOrBlank()) processCommand(text)
                status.text = "Sistema pronto."
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts.language = Locale("pt", "BR")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
