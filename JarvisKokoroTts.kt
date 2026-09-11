package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Local, serial TTS for Kokoro's bm_george voice (speaker 26). */
object JarvisKokoroTts {
    private const val TAG = "JarvisKokoroTts"
    private const val MODEL_DIR = "kokoro"
    private const val GEORGE_SID = 26

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineLock = Any()

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var currentTrack: AudioTrack? = null

    fun speak(context: Context, text: String, onComplete: (() -> Unit)? = null) {
        if (text.isBlank()) {
            mainHandler.post { onComplete?.invoke() }
            return
        }

        scope.launch {
            try {
                val tts = getEngine(context.applicationContext)
                val audio = tts.generateWithConfig(
                    text = text,
                    config = GenerationConfig(
                        sid = GEORGE_SID,
                        speed = 0.92f,
                        silenceScale = 0.2f,
                    ),
                )
                require(audio.samples.isNotEmpty()) { "Kokoro returned no audio" }
                play(audio.samples, audio.sampleRate)
            } catch (error: Throwable) {
                // A TTS failure must never end the microphone foreground service.
                Log.e(TAG, "Kokoro could not synthesize speech", error)
            } finally {
                mainHandler.post { onComplete?.invoke() }
            }
        }
    }

    private fun getEngine(context: Context): OfflineTts {
        engine?.let { return it }

        synchronized(engineLock) {
            engine?.let { return it }

            val dataDir = File(context.filesDir, "kokoro-espeak-ng-data")
            if (!dataDir.exists()) {
                copyAssetTree(context, "$MODEL_DIR/espeak-ng-data", dataDir)
            }

            return OfflineTts(
                assetManager = context.assets,
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        kokoro = OfflineTtsKokoroModelConfig(
                            model = "$MODEL_DIR/model.int8.onnx",
                            voices = "$MODEL_DIR/voices.bin",
                            tokens = "$MODEL_DIR/tokens.txt",
                            dataDir = dataDir.absolutePath,
                            lexicon = "$MODEL_DIR/lexicon-gb-en.txt",
                            lang = "eng",
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu",
                    ),
                    maxNumSentences = 1,
                    silenceScale = 0.2f,
                ),
            ).also { engine = it }
        }
    }

    private fun copyAssetTree(context: Context, path: String, destination: File) {
        val children = context.assets.list(path) ?: emptyArray()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(path).use { input ->
                destination.outputStream().use(input::copyTo)
            }
            return
        }

        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree(context, "$path/$child", File(destination, child))
        }
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        val pcm = ShortArray(samples.size) { index ->
            (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuffer > 0) { "Invalid AudioTrack buffer size: $minBuffer" }

        stopPlayback()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        currentTrack = track
        try {
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                val written = track.write(
                    pcm,
                    offset,
                    pcm.size - offset,
                    AudioTrack.WRITE_BLOCKING,
                )
                check(written > 0) { "AudioTrack write failed: $written" }
                offset += written
            }

            while (
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition < pcm.size
            ) {
                Thread.sleep(20)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            if (currentTrack === track) currentTrack = null
        }
    }

    private fun stopPlayback() {
        currentTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
        currentTrack = null
    }

    fun release() {
        stopPlayback()
        synchronized(engineLock) {
            runCatching { engine?.release() }
            engine = null
        }
    }
}
