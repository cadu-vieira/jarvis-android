package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object JarvisKokoroTts {

    private const val MODEL_DIR = "kokoro-multi-lang-v1_0"
    private const val MODEL_NAME = "model.int8.onnx"
    private const val VOICES = "voices.bin"
    private const val TOKENS = "tokens.txt"
    private const val LEXICON = "lexicon-gb-en.txt"

    // Kokoro v1.0: bm_george = speaker ID 26.
    private const val GEORGE_SID = 26

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    @Volatile
    private var engine: OfflineTts? = null

    @Volatile
    private var initializing = false

    @Volatile
    private var currentTrack: AudioTrack? = null

    fun speak(
        context: Context,
        text: String,
        onComplete: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }

        scope.launch {
            try {
                stopPlayback()

                val tts = getEngine(context.applicationContext)

                val audio = tts.generate(
                    text = text,
                    sid = GEORGE_SID,
                    speed = 0.95f
                )

                play(audio.samples, audio.sampleRate)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun getEngine(context: Context): OfflineTts {
        engine?.let { return it }

        synchronized(this) {
            engine?.let { return it }

            val dataDir = copyEspeakData(context)

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = "$MODEL_DIR/$MODEL_NAME",
                        voices = "$MODEL_DIR/$VOICES",
                        tokens = "$MODEL_DIR/$TOKENS",
                        dataDir = dataDir,
                        lexicon = "$MODEL_DIR/$LEXICON",
                        lang = "eng"
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                maxNumSentences = 1,
                silenceScale = 0.2f
            )

            engine = OfflineTts(
                assetManager = context.assets,
                config = config
            )

            return engine!!
        }
    }

    private fun copyEspeakData(context: Context): String {
        val target = File(
            context.filesDir,
            "$MODEL_DIR/espeak-ng-data"
        )

        if (target.exists()) {
            return target.absolutePath
        }

        copyAssetDirectory(
            context,
            "$MODEL_DIR/espeak-ng-data",
            target
        )

        return target.absolutePath
    }

    private fun copyAssetDirectory(
        context: Context,
        assetPath: String,
        destination: File
    ) {
        destination.mkdirs()

        val children = context.assets.list(assetPath) ?: emptyArray()

        if (children.isEmpty()) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        for (child in children) {
            copyAssetDirectory(
                context,
                "$assetPath/$child",
                File(destination, child)
            )
        }
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        stopPlayback()

        val pcm = ShortArray(samples.size)

        for (i in samples.indices) {
            val value = samples[i].coerceIn(-1f, 1f)
            pcm[i] = (value * Short.MAX_VALUE).toInt().toShort()
        }

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = maxOf(
            minBuffer,
            pcm.size * 2
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        currentTrack = track

        track.write(
            pcm,
            0,
            pcm.size
        )

        track.play()

        while (
            track.playState == AudioTrack.PLAYSTATE_PLAYING &&
            track.playbackHeadPosition < pcm.size
        ) {
            Thread.sleep(20)
        }

        track.stop()
        track.release()

        if (currentTrack === track) {
            currentTrack = null
        }
    }

    fun stopPlayback() {
        try {
            currentTrack?.stop()
        } catch (_: Exception) {
        }

        try {
            currentTrack?.release()
        } catch (_: Exception) {
        }

        currentTrack = null
    }

    fun release() {
        stopPlayback()

        synchronized(this) {
            try {
                engine?.release()
            } catch (_: Exception) {
            }
            engine = null
        }
    }
}
