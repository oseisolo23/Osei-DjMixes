package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import android.media.AudioFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MixRecorder(private val outputFile: File, private val sampleRate: Int = 44100, private val channels: Int = 2) {

    private var fos: FileOutputStream? = null
    private var totalDataSize: Long = 0

    
    
    private val bitsPerSample = 16

    fun start() {
        try {
            fos = FileOutputStream(outputFile)
            writeWavHeader(fos!!, 0)
            totalDataSize = 0
            Log.d("MixRecorder", "Recording started: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("MixRecorder", "Failed to start recording", e)
        }
    }

    fun write(buffer: ByteBuffer) {
        val size = buffer.remaining()
        val data = ByteArray(size)
        buffer.get(data)
        fos?.write(data)
        totalDataSize += size
    }

    fun getOutputFile(): File = outputFile

    fun stop() {
        try {
            fos?.close()
            updateWavHeader()
            fos = null
            Log.d("MixRecorder", "Recording stopped. Size: $totalDataSize")
        } catch (e: Exception) {
            Log.e("MixRecorder", "Failed to stop recording", e)
        }
    }

    private fun updateWavHeader() {
        if (!outputFile.exists() || totalDataSize <= 0) return
        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(4)
                raf.writeInt(Integer.reverseBytes((totalDataSize + 36).toInt()))
                raf.seek(40)
                raf.writeInt(Integer.reverseBytes(totalDataSize.toInt()))
            }
        } catch (e: Exception) {
            Log.e("MixRecorder", "Error updating WAV header size", e)
        }
    }

    private fun writeWavHeader(out: FileOutputStream, totalSize: Long) {
        val header = ByteArray(44)
        val byteOrder = ByteOrder.LITTLE_ENDIAN
        val buffer = ByteBuffer.wrap(header).order(byteOrder)

        buffer.put("RIFF".toByteArray())
        buffer.putInt((totalSize + 36).toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size
        buffer.putShort(1.toShort()) // AudioFormat (PCM)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * bitsPerSample / 8) // ByteRate
        buffer.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(totalSize.toInt())

        out.write(header)
    }
}
