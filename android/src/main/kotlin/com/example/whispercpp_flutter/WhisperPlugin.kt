package com.example.whispercpp_flutter

import android.content.Context
import android.content.res.AssetFileDescriptor
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WhisperPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
  private lateinit var channel: MethodChannel
  private var applicationContext: Context? = null
  private var executor: ExecutorService? = null
  private var recorderController: RecorderController? = null

  companion object {
    const val DEFAULT_MODEL_FILE = "ggml-medium-q5_0.bin"

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
      "getBundledModelPath" -> handleGetBundledModelPath(call, result)
      "startRecording" -> handleStartRecording(result)
      "stopRecording" -> {
        val ex = executor
        if (ex == null) {
          result.error("NO_CONTEXT", "Plugin not attached.", null)
          return
        }
        ex.execute {
          try {
            val path = stopRecordingSync()
            if (path == null) {
              result.error("NOT_RECORDING", "No active recording.", null)
            } else {
              result.success(path)
            }
          } catch (e: Exception) {
            result.error("STOP_FAILED", e.message, null)
          }
        }
      }
      "stopAndTranscribe" -> {
        val ctx = applicationContext
        val ex = executor
        if (ctx == null || ex == null) {
          result.error("NO_CONTEXT", "Plugin not attached.", null)
          return
        }
        val modelPathArg = call.argument<String>("modelPath")
        val language = call.argument<String>("language") ?: "auto"
        ex.execute {
          try {
            val wavPath =
              stopRecordingSync()
                ?: run {
                  result.error("NOT_RECORDING", "No active recording.", null)
                  return@execute
                }
            val modelPath =
              modelPathArg ?: ensureBundledModelCopied(ctx, DEFAULT_MODEL_FILE)
            val text = transcribeNative(modelPath, wavPath, language)
            result.success(text)
          } catch (e: Exception) {
            result.error("TRANSCRIBE_FAILED", e.message, null)
          }
        }
      }
      "transcribe" -> {
        val ctx = applicationContext
        val ex = executor
        if (ctx == null || ex == null) {
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
        ex.execute {
          try {
            val modelPath =
              modelPathArg ?: ensureBundledModelCopied(ctx, DEFAULT_MODEL_FILE)
            val text = transcribeNative(modelPath, audioPath, language)
            result.success(text)
          } catch (e: Exception) {
            result.error("TRANSCRIBE_FAILED", e.message, null)
          }
        }
      }
      else -> result.notImplemented()
    }
  }

  private fun handleGetBundledModelPath(call: MethodCall, result: MethodChannel.Result) {
    val ctx = applicationContext
    val ex = executor
    if (ctx == null || ex == null) {
      result.error("NO_CONTEXT", "Plugin not attached.", null)
      return
    }
    val name = call.argument<String>("modelFileName") ?: DEFAULT_MODEL_FILE
    ex.execute {
      try {
        val path = ensureBundledModelCopied(ctx, name)
        result.success(path)
      } catch (e: Exception) {
        result.error("MODEL_COPY_FAILED", e.message, null)
      }
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

  private fun isBundledModelUsable(
    output: File,
    marker: File,
    expectedFromAsset: Long,
  ): Boolean {
    if (!output.isFile || !marker.isFile) {
      return false
    }
    val sizeFromMarker =
      try {
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

  private fun ensureBundledModelCopied(context: Context, modelFileName: String): String {
    if (modelFileName.contains('/') || modelFileName.contains('\\')) {
      throw IllegalArgumentException("modelFileName must be a file name, not a path.")
    }
    val baseName = File(modelFileName).name
    if (baseName.isBlank() || baseName != modelFileName.trim()) {
      throw IllegalArgumentException("Invalid modelFileName.")
    }
    val assetPath = "models/$baseName"
    val modelsDir = File(context.filesDir, "models")
    if (!modelsDir.exists() && !modelsDir.mkdirs()) {
      throw IOException("Failed to create models directory.")
    }
    val outFile = File(modelsDir, baseName)
    val markerFile = File(modelsDir, "$baseName.length")

    val expectedLength =
      try {
        context.assets.openFd(assetPath).use { fd ->
          val len = fd.length
          if (len == AssetFileDescriptor.UNKNOWN_LENGTH) {
            -1L
          } else {
            len
          }
        }
      } catch (_: Exception) {
        -1L
      }

    if (isBundledModelUsable(outFile, markerFile, expectedLength)) {
      return outFile.absolutePath
    }

    if (outFile.exists() && !outFile.delete()) {
      throw IOException("Failed to remove an incomplete or stale model file. Clear app data and retry.")
    }
    if (markerFile.exists() && !markerFile.delete()) {
      throw IOException("Failed to remove stale model metadata. Clear app data and retry.")
    }

    val temp = File.createTempFile("whisper_bundle_", ".part", modelsDir)
    try {
      BufferedInputStream(context.assets.open(assetPath)).use { input ->
        FileOutputStream(temp).use { output ->
          val written = input.copyTo(output)
          output.fd.sync()
          if (expectedLength > 0L && written != expectedLength) {
            throw IOException(
              "Bundled model size mismatch (expected $expectedLength bytes, copied $written).",
            )
          }
        }
      }
      if (expectedLength > 0L && temp.length() != expectedLength) {
        throw IOException("Bundled model file is incomplete on disk after extraction.")
      }
      if (!temp.renameTo(outFile)) {
        throw IOException("Failed to finalize the bundled model file after extraction.")
      }
      try {
        markerFile.writeText(outFile.length().toString())
      } catch (e: Exception) {
        outFile.delete()
        markerFile.delete()
        throw e
      }
    } finally {
      if (temp.exists() && !temp.delete()) {
        // best effort
      }
    }
    return outFile.absolutePath
  }
}
