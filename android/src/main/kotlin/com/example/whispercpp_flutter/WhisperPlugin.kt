package com.example.whispercpp_flutter

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class WhisperPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
  private lateinit var channel: MethodChannel
  private var applicationContext: Context? = null
  private val executor = Executors.newSingleThreadExecutor()

  private var audioRecord: AudioRecord? = null
  private var recordThread: Thread? = null

  @Volatile
  private var recordingActive = false

  private var recordingRaf: RandomAccessFile? = null
  private var recordingFile: File? = null

  companion object {
    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val WAV_HEADER_BYTES = 44
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
    applicationContext = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "whispercpp_flutter")
    channel.setMethodCallHandler(this)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    applicationContext = null
    executor.shutdownNow()
    synchronized(this) {
      recordingActive = false
      try {
        recordThread?.interrupt()
        recordThread?.join(2000)
      } catch (_: InterruptedException) {
      }
      recordThread = null
      try {
        audioRecord?.stop()
        audioRecord?.release()
      } catch (_: Exception) {
      }
      audioRecord = null
      try {
        recordingRaf?.close()
      } catch (_: Exception) {
      }
      recordingRaf = null
      recordingFile = null
    }
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "getBundledModelPath" -> handleGetBundledModelPath(call, result)
      "startRecording" -> handleStartRecording(result)
      "stopRecording" -> {
        executor.execute {
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
        if (ctx == null) {
          result.error("NO_CONTEXT", "Plugin not attached.", null)
          return
        }
        val modelPathArg = call.argument<String>("modelPath")
        val language = call.argument<String>("language") ?: "auto"
        executor.execute {
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
        if (ctx == null) {
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
        executor.execute {
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
    if (ctx == null) {
      result.error("NO_CONTEXT", "Plugin not attached.", null)
      return
    }
    try {
      val name = call.argument<String>("modelFileName") ?: DEFAULT_MODEL_FILE
      val path = ensureBundledModelCopied(ctx, name)
      result.success(path)
    } catch (e: Exception) {
      result.error("MODEL_COPY_FAILED", e.message, null)
    }
  }

  private fun handleStartRecording(result: MethodChannel.Result) {
    synchronized(this) {
      val ctx = applicationContext
      if (ctx == null) {
        result.error("NO_CONTEXT", "Plugin not attached.", null)
        return
      }
      if (audioRecord != null) {
        result.error("ALREADY_RECORDING", "Recording already in progress.", null)
        return
      }
      val bufferSize =
        AudioRecord.getMinBufferSize(
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
        result.error("AUDIO_INIT_FAILED", "getMinBufferSize failed.", null)
        return
      }
      val file = File(ctx.cacheDir, "whisper_${System.currentTimeMillis()}.wav")
      recordingFile = file
      val raf =
        try {
          RandomAccessFile(file, "rw")
        } catch (e: Exception) {
          recordingFile = null
          result.error("IO_ERROR", e.message, null)
          return
        }
      recordingRaf = raf
      try {
        raf.setLength(0)
        raf.write(ByteArray(WAV_HEADER_BYTES))
      } catch (e: Exception) {
        try {
          raf.close()
        } catch (_: Exception) {
        }
        recordingRaf = null
        recordingFile = null
        result.error("IO_ERROR", e.message, null)
        return
      }
      val recorder =
        AudioRecord(
          MediaRecorder.AudioSource.MIC,
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize * 2,
        )
      if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        try {
          raf.close()
        } catch (_: Exception) {
        }
        recordingRaf = null
        recordingFile = null
        result.error("AUDIO_INIT_FAILED", "AudioRecord not initialized.", null)
        return
      }
      recordingActive = true
      audioRecord = recorder
      recorder.startRecording()
      val captureBufferSize = maxOf(bufferSize / 2, 256)
      recordThread =
        Thread {
          val shortBuf = ShortArray(captureBufferSize)
          try {
            while (recordingActive) {
              val n = recorder.read(shortBuf, 0, shortBuf.size)
              if (n > 0) {
                val out = recordingRaf ?: break
                for (i in 0 until n) {
                  val s = shortBuf[i]
                  out.write(s.toInt() and 0xFF)
                  out.write((s.toInt() shr 8) and 0xFF)
                }
              }
            }
          } catch (_: Exception) {
          }
        }
      recordThread!!.start()
      result.success(file.absolutePath)
    }
  }

  private fun stopRecordingSync(): String? {
    synchronized(this) {
      if (audioRecord == null) {
        return null
      }
      recordingActive = false
      try {
        recordThread?.join(8000)
      } catch (_: InterruptedException) {
      }
      recordThread = null
      try {
        audioRecord?.stop()
        audioRecord?.release()
      } catch (_: Exception) {
      }
      audioRecord = null
      val raf = recordingRaf
      val file = recordingFile
      recordingRaf = null
      recordingFile = null
      if (raf == null || file == null) {
        return null
      }
      return try {
        val pcmSize = (raf.length() - WAV_HEADER_BYTES).toInt()
        if (pcmSize < 0) {
          raf.close()
          return null
        }
        writePcmWavHeader(raf, pcmSize, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE)
        raf.close()
        file.absolutePath
      } catch (_: Exception) {
        try {
          raf.close()
        } catch (_: Exception) {
        }
        null
      }
    }
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
    val outFile = File(context.filesDir, "models/$baseName")
    outFile.parentFile?.mkdirs()
    if (!outFile.exists() || outFile.length() == 0L) {
      context.assets.open(assetPath).use { input ->
        outFile.outputStream().use { output -> input.copyTo(output) }
      }
    }
    return outFile.absolutePath
  }

  private fun writePcmWavHeader(
    raf: RandomAccessFile,
    pcmDataSize: Int,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
  ) {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val chunkSize = 36 + pcmDataSize
    val buffer =
      ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt(chunkSize)
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)
        putShort(1)
        putShort(channels.toShort())
        putInt(sampleRate)
        putInt(byteRate)
        putShort(blockAlign.toShort())
        putShort(bitsPerSample.toShort())
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(pcmDataSize)
      }
    raf.seek(0)
    raf.write(buffer.array())
  }
}
