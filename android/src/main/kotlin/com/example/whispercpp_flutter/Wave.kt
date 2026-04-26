package com.example.whispercpp_flutter

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun encodeWaveFile(file: File, data: ShortArray) {
    file.outputStream().use { output ->
        output.write(waveHeaderBytes(data.size * 2))
        val buffer = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(data)
        output.write(buffer.array())
    }
}

private fun waveHeaderBytes(dataLength: Int): ByteArray {
    val totalLength = dataLength + 44
    return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put('R'.code.toByte())
        put('I'.code.toByte())
        put('F'.code.toByte())
        put('F'.code.toByte())
        putInt(totalLength - 8)
        put('W'.code.toByte())
        put('A'.code.toByte())
        put('V'.code.toByte())
        put('E'.code.toByte())
        put('f'.code.toByte())
        put('m'.code.toByte())
        put('t'.code.toByte())
        put(' '.code.toByte())
        putInt(16)
        putShort(1.toShort())
        putShort(1.toShort())
        putInt(16_000)
        putInt(32_000)
        putShort(2.toShort())
        putShort(16.toShort())
        put('d'.code.toByte())
        put('a'.code.toByte())
        put('t'.code.toByte())
        put('a'.code.toByte())
        putInt(dataLength)
    }.array()
}
