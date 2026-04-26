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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

internal class RecorderController {
    private val scope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )
    private var recorder: AudioRecordThread? = null

    suspend fun startRecording(outputFile: File) = withContext(scope.coroutineContext) {
        check(recorder == null) { "A recording is already in progress." }
        val nextRecorder = AudioRecordThread(outputFile)
        recorder = nextRecorder
        nextRecorder.start()
        nextRecorder.awaitStarted()
    }

    suspend fun stopRecording(): File = withContext(scope.coroutineContext) {
        val current = recorder ?: throw IllegalStateException("No recording is in progress.")
        current.stopRecording()
        current.join()
        recorder = null
        current.throwIfFailed()
        current.outputFile
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
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ) * 4
            require(bufferSize > 0) { "Failed to initialize microphone buffer." }

            val buffer = ShortArray(bufferSize / 2)
            val audioRecord = AudioRecord(
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

    fun stopRecording() {
        quit.set(true)
    }

    fun throwIfFailed() {
        failure?.let { throw it }
    }
}
