package com.example.whispercpp_flutter

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Serializes microphone capture on a single worker thread. Blocking API so the plugin can call
 * from the method-channel thread or its inference executor without coroutines.
 */
internal class RecorderController {
  private val ctrl = Executors.newSingleThreadExecutor()
  private var activeCapture: AudioRecordThread? = null

  fun isRecording(): Boolean {
    val out = AtomicBoolean(false)
    val done = CountDownLatch(1)
    ctrl.execute {
      out.set(activeCapture != null)
      done.countDown()
    }
    check(done.await(5, TimeUnit.SECONDS)) { "Timed out checking recorder state." }
    return out.get()
  }

  fun startRecording(outputFile: File) {
    val done = CountDownLatch(1)
    val error = AtomicReference<Exception?>(null)
    ctrl.execute {
      try {
        check(activeCapture == null) { "A recording is already in progress." }
        val thread = AudioRecordThread(outputFile)
        activeCapture = thread
        thread.start()
        thread.awaitStarted()
      } catch (e: Exception) {
        error.set(e as? Exception ?: RuntimeException(e))
        activeCapture = null
      } finally {
        done.countDown()
      }
    }
    done.await()
    error.get()?.let { throw it }
  }

  fun stopRecording(): File {
    val done = CountDownLatch(1)
    val outFile = AtomicReference<File?>(null)
    val error = AtomicReference<Exception?>(null)
    ctrl.execute {
      try {
        val current =
          activeCapture ?: throw IllegalStateException("No recording is in progress.")
        current.stopCapture()
        current.join(8_000)
        activeCapture = null
        current.throwIfFailed()
        outFile.set(current.outputFile)
      } catch (e: Exception) {
        error.set(e as? Exception ?: RuntimeException(e))
        activeCapture = null
      } finally {
        done.countDown()
      }
    }
    done.await()
    error.get()?.let { throw it }
    return outFile.get()!!
  }

  fun shutdown() {
    val done = CountDownLatch(1)
    ctrl.execute {
      try {
        activeCapture?.stopCapture()
        activeCapture?.join(2_000)
      } catch (_: Exception) {
      }
      activeCapture = null
      done.countDown()
    }
    done.await(3, TimeUnit.SECONDS)
    ctrl.shutdownNow()
  }
}

private class AudioRecordThread(
  val outputFile: File,
) : Thread("WhisperPluginAudioRecorder") {
  private val quit = AtomicBoolean(false)
  private val started = CountDownLatch(1)

  @Volatile
  private var failure: Exception? = null

  @SuppressLint("MissingPermission")
  override fun run() {
    try {
      val sampleRate = 16_000
      val bufferSize =
        AudioRecord.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        ) * 4
      require(bufferSize > 0) { "Failed to initialize microphone buffer." }

      val buffer = ShortArray(bufferSize / 2)
      val audioRecord =
        AudioRecord(
          MediaRecorder.AudioSource.MIC,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize,
        )

      try {
        require(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
          "Failed to initialize microphone recorder."
        }
        audioRecord.startRecording()
        require(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          "Microphone recording did not start."
        }
        started.countDown()

        val allData = ArrayList<Short>(buffer.size * 32)
        while (!quit.get()) {
          val read = audioRecord.read(buffer, 0, buffer.size)
          if (read > 0) {
            for (index in 0 until read) {
              allData.add(buffer[index])
            }
          } else {
            throw IllegalStateException("audioRecord.read returned $read")
          }
        }

        audioRecord.stop()
        encodeWaveFile(outputFile, allData.toShortArray())
      } finally {
        started.countDown()
        audioRecord.release()
      }
    } catch (error: Exception) {
      failure = error
      started.countDown()
    }
  }

  fun awaitStarted() {
    check(started.await(5, TimeUnit.SECONDS)) { "Timed out while starting microphone capture." }
    throwIfFailed()
  }

  fun stopCapture() {
    quit.set(true)
  }

  fun throwIfFailed() {
    failure?.let { throw it }
  }
}
