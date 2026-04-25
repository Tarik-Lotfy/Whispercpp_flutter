package com.example.whispercpp_flutter

import android.content.res.AssetFileDescriptor
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WhisperPlugin : FlutterPlugin, MethodCallHandler {
    private val bundledTinyAssetPath = "models/ggml-tiny.bin"
    private val englishOnlyModelPattern = Regex("""\.en(\.|$)""")

    companion object {
        init {
            System.loadLibrary("whispercpp_flutter")
        }
    }

    private lateinit var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding
    private lateinit var channel: MethodChannel

    private val transcribeJob = SupervisorJob()
    private val pluginScope = CoroutineScope(transcribeJob + Dispatchers.Main.immediate)

    private external fun transcribeNative(
        modelPath: String,
        audioPath: String,
        language: String,
    ): String

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = binding
        channel = MethodChannel(binding.binaryMessenger, "whispercpp_flutter")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getBundledTinyModelPath" -> {
                pluginScope.launch {
                    try {
                        val path = getBundledTinyModelPath()
                        result.success(path)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        result.error("bundled_model_unavailable", e.message, null)
                    } catch (e: Throwable) {
                        result.error(
                            "bundled_model_unavailable",
                            e.message ?: "Failed to prepare the bundled tiny model.",
                            null,
                        )
                    }
                }
            }
            "transcribe" -> handleTranscribe(call, result)
            else -> result.notImplemented()
        }
    }

    private fun handleTranscribe(call: MethodCall, result: Result) {
        val requestedModelPath = call.argument<String>("modelPath")
        val audioPath = call.argument<String>("audioPath")
        val language = normalizeLanguage(call.argument<String>("language"))

        if (audioPath.isNullOrBlank()) {
            result.error("invalid_args", "audioPath is required.", null)
            return
        }

        // Resolve bundled model and run inference off the main thread; complete Result on the main thread.
        pluginScope.launch {
            val modelPath = try {
                resolveModelPath(requestedModelPath)
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                result.error(
                    "bundled_model_unavailable",
                    error.message ?: "Failed to prepare the bundled tiny model.",
                    null,
                )
                return@launch
            }

            if (!isLanguageCompatibleWithModel(language, modelPath)) {
                result.error(
                    "unsupported_model",
                    "Non-English transcription requires a multilingual Whisper model. " +
                        "Use a model like ggml-tiny.bin instead of an English-only .en model.",
                    null,
                )
                return@launch
            }

            val modelFile = File(modelPath)
            if (!modelFile.exists() || !modelFile.isFile) {
                result.error("model_not_found", "Model file was not found at: $modelPath", null)
                return@launch
            }

            val audioFile = File(audioPath)
            if (!audioFile.exists() || !audioFile.isFile) {
                result.error("audio_not_found", "Audio file was not found at: $audioPath", null)
                return@launch
            }

            try {
                val text = withContext(Dispatchers.Default) {
                    transcribeNative(modelPath, audioPath, language)
                }
                result.success(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                result.error("transcription_failed", e.message, null)
            } catch (e: Throwable) {
                result.error(
                    "native_error",
                    e.message ?: "Unknown native transcription error.",
                    null,
                )
            }
        }
    }

    private fun normalizeLanguage(language: String?): String {
        val normalized = language
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace('_', '-')
            .orEmpty()

        return if (normalized.isEmpty()) "auto" else normalized
    }

    private fun isLanguageCompatibleWithModel(language: String, modelPath: String): Boolean {
        if (language == "auto" || language == "en") {
            return true
        }

        return !englishOnlyModelPattern.containsMatchIn(modelPath.lowercase(Locale.ROOT))
    }

    private suspend fun resolveModelPath(requestedModelPath: String?): String {
        return if (requestedModelPath.isNullOrBlank()) {
            getBundledTinyModelPath()
        } else {
            requestedModelPath
        }
    }

    /**
     * Installs the bundled model into [filesDir] with an atomic final rename. A [markerFile] records
     * the last successful size so a truncated or interrupted copy cannot be mistaken for a valid model.
     * Asset unpacking and file I/O run on [Dispatchers.IO].
     */
    @Throws(IOException::class)
    private suspend fun getBundledTinyModelPath(): String = withContext(Dispatchers.IO) {
        val context = flutterPluginBinding.applicationContext
        val modelsDir = File(context.filesDir, "whispercpp_flutter/models")
        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            throw IOException("Failed to create models directory.")
        }
        val outputFile = File(modelsDir, "ggml-tiny.bin")
        val markerFile = File(modelsDir, "ggml-tiny.bin.length")

        val expectedLength = context.assets.openFd(bundledTinyAssetPath).use { fd: AssetFileDescriptor ->
            val len = fd.length
            if (len == AssetFileDescriptor.UNKNOWN_LENGTH) {
                -1L
            } else {
                len
            }
        }

        if (isBundledModelUsable(outputFile, markerFile, expectedLength)) {
            return@withContext outputFile.absolutePath
        }

        if (outputFile.exists() && !outputFile.delete()) {
            throw IOException("Failed to remove an incomplete or stale model file. Clear app data and retry.")
        }
        if (markerFile.exists() && !markerFile.delete()) {
            throw IOException("Failed to remove stale model metadata. Clear app data and retry.")
        }

        val temp = File.createTempFile("ggml-tiny", ".part", modelsDir)
        try {
            BufferedInputStream(context.assets.open(bundledTinyAssetPath)).use { input ->
                FileOutputStream(temp).use { output ->
                    val written = input.copyTo(output)
                    if (expectedLength > 0L && written != expectedLength) {
                        throw IOException(
                            "Bundled model size mismatch (expected $expectedLength bytes, copied $written).",
                        )
                    }
                    if (output.fd.valid()) {
                        output.fd.sync()
                    }
                }
            }
            if (expectedLength > 0L && temp.length() != expectedLength) {
                throw IOException("Bundled model file is incomplete on disk after extraction.")
            }
            if (!temp.renameTo(outputFile)) {
                throw IOException("Failed to finalize the bundled model file after extraction.")
            }
            // Marker written only after the model file is fully installed, so a partial copy cannot look valid.
            markerFile.writeText(outputFile.length().toString())
        } finally {
            if (temp.exists() && !temp.delete()) {
                // Best-effort: leave no stray temp; ignore failure
            }
        }
        return@withContext outputFile.absolutePath
    }

    private fun isBundledModelUsable(
        output: File,
        marker: File,
        expectedFromAsset: Long,
    ): Boolean {
        if (!output.isFile || !marker.isFile) {
            return false
        }
        val sizeFromMarker = try {
            marker.readText().trim().toLong()
        } catch (_: NumberFormatException) {
            return false
        }
        if (sizeFromMarker != output.length()) {
            return false
        }
        if (expectedFromAsset > 0L) {
            return output.length() == expectedFromAsset
        }
        return true
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        transcribeJob.cancel()
    }
}
