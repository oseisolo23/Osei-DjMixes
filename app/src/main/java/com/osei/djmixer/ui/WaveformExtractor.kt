package com.osei.djmixer.ui

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Extracts real audio waveform peak data from loaded audio files.
 * Caches extracted peaks in memory to avoid redundant re-analysis.
 * Provides genuine audio peak values normalized 0.0f..1.0f.
 */
object WaveformExtractor {
    private const val TAG = "WaveformExtractor"
    private val memoryCache = LruCache<String, FloatArray>(32)

    suspend fun extractWaveform(
        context: Context,
        uriString: String,
        targetPoints: Int = 400
    ): FloatArray? = withContext(Dispatchers.IO) {
        val cached = memoryCache.get(uriString)
        if (cached != null) return@withContext cached

        val uri = Uri.parse(uriString)

        // Fast path for WAV files: read PCM directly without MediaCodec
        if (uriString.lowercase().endsWith(".wav") || uriString.lowercase().contains(".wav?")) {
            val wavPeaks = extractWavDirect(context, uri, targetPoints)
            if (wavPeaks != null) {
                memoryCache.put(uriString, wavPeaks)
                return@withContext wavPeaks
            }
        }

        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open data source for waveform: $uriString")
                return@withContext null
            }

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }

            if (trackIndex == -1 || format == null) return@withContext null

            extractor.selectTrack(trackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                300_000_000L
            }

            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            try {
                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()
            } catch (t: Throwable) {
                Log.w(TAG, "MediaCodec not available for $mime: ${t.message}")
                return@withContext null
            }

            val info = MediaCodec.BufferInfo()
            val points = FloatArray(targetPoints)
            val binDurationUs = (durationUs / targetPoints).coerceAtLeast(1000L)

            var isExtractorEOS = false
            var isDecoderEOS = false

            val maxDecodeTimeoutUs = 4000L
            var iterations = 0
            val maxIterations = 5000

            while (!isDecoderEOS && iterations < maxIterations) {
                iterations++
                if (!isExtractorEOS) {
                    val inIdx = decoder.dequeueInputBuffer(maxDecodeTimeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEOS = true
                            } else {
                                val timeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, timeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(info, maxDecodeTimeoutUs)
                if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.asShortBuffer()
                        var maxPeak = 0
                        while (shortBuf.hasRemaining()) {
                            val sample = abs(shortBuf.get().toInt())
                            if (sample > maxPeak) maxPeak = sample
                        }
                        val bin = ((info.presentationTimeUs / binDurationUs).toInt()).coerceIn(0, targetPoints - 1)
                        val norm = (maxPeak / 32768.0f).coerceIn(0.0f, 1.0f)
                        if (norm > points[bin]) {
                            points[bin] = norm
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }
                }
            }

            var lastVal = 0.1f
            for (i in points.indices) {
                if (points[i] > 0f) {
                    lastVal = points[i]
                } else {
                    points[i] = (lastVal * 0.7f).coerceAtLeast(0.05f)
                }
            }

            memoryCache.put(uriString, points)
            return@withContext points
        } catch (e: Exception) {
            Log.e(TAG, "Waveform extraction failed for $uriString", e)
            return@withContext null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
                extractor?.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    private fun extractWavDirect(context: Context, uri: Uri, targetPoints: Int): FloatArray? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val header = ByteArray(44)
            val headerRead = inputStream.read(header)
            if (headerRead < 44) return null

            val byteBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") return null

            val channels = byteBuffer.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = byteBuffer.getInt(24).coerceAtLeast(8000)
            val bitsPerSample = byteBuffer.getShort(34).toInt().coerceAtLeast(8)
            val dataSize = byteBuffer.getInt(40).toLong().and(0xFFFFFFFFL)

            val bytesPerSample = (bitsPerSample / 8) * channels
            val totalSamples = dataSize / bytesPerSample
            if (totalSamples <= 0) return null

            val points = FloatArray(targetPoints)
            val samplesPerBin = (totalSamples / targetPoints).coerceAtLeast(1L)
            val buffer = ByteArray((samplesPerBin * bytesPerSample).toInt().coerceIn(1024, 65536))

            var bin = 0
            while (bin < targetPoints) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead <= 0) break
                val shortsRead = bytesRead / 2
                val shortBuf = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                var maxPeak = 0
                while (shortBuf.hasRemaining()) {
                    val s = abs(shortBuf.get().toInt())
                    if (s > maxPeak) maxPeak = s
                }
                points[bin] = (maxPeak / 32768.0f).coerceIn(0.0f, 1.0f)
                bin++
            }
            return points
        } catch (e: Exception) {
            return null
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }
}
