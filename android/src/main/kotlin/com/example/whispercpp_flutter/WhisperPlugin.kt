package com.example.whispercpp_flutter

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

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
            "getBundledTinyModelPath" -> result.success(getBundledTinyModelPath())
            "transcribe" -> handleTranscribe(call, result)
            else -> result.notImplemented()
        }
    }

    private fun handleTranscribe(call: MethodCall, result: Result) {
        val requestedModelPath = call.argument<String>("modelPath")
        val audioPath = call.argument<String>("audioPath")
        val language = normalizeLanguage(call.argument<String>("language"))
        val modelPath = try {
            resolveModelPath(requestedModelPath)
        } catch (error: Throwable) {
            result.error(
                "bundled_model_unavailable",
                error.message ?: "Failed to prepare the bundled tiny model.",
                null,
            )
            return
        }

        if (audioPath.isNullOrBlank()) {
            result.error("invalid_args", "audioPath is required.", null)
            return
        }

        if (!isLanguageCompatibleWithModel(language, modelPath)) {
            result.error(
                "unsupported_model",
                "Non-English transcription requires a multilingual Whisper model. " +
                    "Use a model like ggml-tiny.bin instead of an English-only .en model.",
                null,
            )
            return
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.isFile) {
            result.error("model_not_found", "Model file was not found at: $modelPath", null)
            return
        }

        val audioFile = File(audioPath)
        if (!audioFile.exists() || !audioFile.isFile) {
            result.error("audio_not_found", "Audio file was not found at: $audioPath", null)
            return
        }

        try {
            result.success(transcribeNative(modelPath, audioPath, language))
        } catch (error: IllegalArgumentException) {
            result.error("transcription_failed", error.message, null)
        } catch (error: Throwable) {
            result.error(
                "native_error",
                error.message ?: "Unknown native transcription error.",
                null,
            )
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

    private fun resolveModelPath(requestedModelPath: String?): String {
        return if (requestedModelPath.isNullOrBlank()) {
            getBundledTinyModelPath()
        } else {
            requestedModelPath
        }
    }

    private fun getBundledTinyModelPath(): String {
        val context = flutterPluginBinding.applicationContext
        val modelsDir = File(context.filesDir, "whispercpp_flutter/models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val outputFile = File(modelsDir, "ggml-tiny.bin")
        if (outputFile.exists() && outputFile.length() > 0L) {
            return outputFile.absolutePath
        }

        BufferedInputStream(context.assets.open(bundledTinyAssetPath)).use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }

        return outputFile.absolutePath
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}
