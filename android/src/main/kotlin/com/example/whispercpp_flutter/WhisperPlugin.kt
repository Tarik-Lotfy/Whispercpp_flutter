package com.example.whispercpp_flutter

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WhisperPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
  private lateinit var channel: MethodChannel
  private var applicationContext: Context? = null
  private var executor: ExecutorService? = null
  private var recorderController: RecorderController? = null

  companion object {
    init {
      System.loadLibrary("whispercpp_flutter")
    }
  }

  private external fun transcribeNative(
    modelPath: String,
    audioPath: String,
    language: String,
  ): String

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    executor?.shutdownNow()
    executor = Executors.newSingleThreadExecutor()
    recorderController = RecorderController()
    applicationContext = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "whispercpp_flutter")
    channel.setMethodCallHandler(this)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    recorderController?.shutdown()
    recorderController = null
    applicationContext = null
    executor?.shutdownNow()
    executor = null
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "startRecording" -> handleStartRecording(result)
      "stopAndTranscribe" -> {
        val ex = executor
        if (ex == null) {
          result.error("NO_CONTEXT", "Plugin not attached.", null)
          return
        }
        val modelPathArg = call.argument<String>("modelPath")
        val language = call.argument<String>("language") ?: "auto"
        if (modelPathArg.isNullOrBlank()) {
          result.error(
            "BAD_ARGUMENT",
            "modelPath is required. Download a GGML model in Dart (see downloadModel) and pass the file path.",
            null,
          )
          return
        }
        if (!File(modelPathArg).isFile) {
          result.error(
            "BAD_ARGUMENT",
            "modelPath must be an existing file: $modelPathArg",
            null,
          )
          return
        }
        ex.execute {
          try {
            val wavPath =
              stopRecordingSync()
                ?: run {
                  result.error("NOT_RECORDING", "No active recording.", null)
                  return@execute
                }
            val text = transcribeNative(modelPathArg, wavPath, language)
            result.success(text)
          } catch (e: Exception) {
            result.error("TRANSCRIBE_FAILED", e.message, null)
          }
        }
      }
      "transcribeFile" -> {
        val ex = executor
        if (ex == null) {
          result.error("NO_CONTEXT", "Plugin not attached.", null)
          return
        }
        val modelPathArg = call.argument<String>("modelPath")
        val audioPath =
          call.argument<String>("audioPath")
            ?: run {
              result.error("BAD_ARGUMENT", "audioPath is required.", null)
              return
            }
        val language = call.argument<String>("language") ?: "auto"
        if (modelPathArg.isNullOrBlank()) {
          result.error(
            "BAD_ARGUMENT",
            "modelPath is required. Download a GGML model in Dart (see downloadModel) and pass the file path.",
            null,
          )
          return
        }
        if (!File(modelPathArg).isFile) {
          result.error(
            "BAD_ARGUMENT",
            "modelPath must be an existing file: $modelPathArg",
            null,
          )
          return
        }
        if (!File(audioPath).isFile) {
          result.error(
            "BAD_ARGUMENT",
            "audioPath must be an existing file: $audioPath",
            null,
          )
          return
        }
        ex.execute {
          try {
            val text = transcribeNative(modelPathArg, audioPath, language)
            result.success(text)
          } catch (e: Exception) {
            result.error("TRANSCRIBE_FAILED", e.message, null)
          }
        }
      }
      else -> result.notImplemented()
    }
  }

  private fun handleStartRecording(result: MethodChannel.Result) {
    val ctx = applicationContext
    val rc = recorderController
    if (ctx == null || rc == null) {
      result.error("NO_CONTEXT", "Plugin not attached.", null)
      return
    }
    try {
      if (rc.isRecording()) {
        result.error("ALREADY_RECORDING", "Recording already in progress.", null)
        return
      }
      val file = File(ctx.cacheDir, "whisper_${System.currentTimeMillis()}.wav")
      rc.startRecording(file)
      result.success(file.absolutePath)
    } catch (e: Exception) {
      result.error("AUDIO_INIT_FAILED", e.message, null)
    }
  }

  private fun stopRecordingSync(): String? {
    val rc = recorderController ?: return null
    return try {
      rc.stopRecording().absolutePath
    } catch (_: IllegalStateException) {
      null
    }
  }
}
