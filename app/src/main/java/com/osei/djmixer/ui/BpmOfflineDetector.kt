package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs

object BpmOfflineDetector {
    private const val TAG = "BpmOfflineDetector"

    suspend fun detectBpm(context: Context, uriString: String): Int? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            val uri = Uri.parse(uriString)
            extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot set dataSource for URI: $uriString", e)
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

            if (trackIndex == -1 || format == null) {
                Log.e(TAG, "No audio track found in $uriString")
                return@withContext null
            }

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            } else {
                44100
            }

            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                2
            }

            val info = MediaCodec.BufferInfo()

            // Analyze up to 25 seconds of audio
            val maxSecondsToAnalyze = 25
            val samplesToAnalyze = sampleRate * channels * maxSecondsToAnalyze
            var totalSamplesRead = 0

            val blockSamples = 1024
            var blockEnergySum = 0.0
            var sampleInBlockCount = 0
            val energyList = ArrayList<Double>()

            var isExtractorEOS = false
            var isDecoderEOS = false

            // Use non-deprecated or robust API for buffers
            while (!isDecoderEOS && totalSamplesRead < samplesToAnalyze) {
                if (!isExtractorEOS) {
                    val inputBufIndex = decoder.dequeueInputBuffer(10000)
                    if (inputBufIndex >= 0) {
                        val dstBuf = decoder.getInputBuffer(inputBufIndex)
                        if (dstBuf != null) {
                            dstBuf.clear()
                            val sampleSize = extractor.readSampleData(dstBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEOS = true
                            } else {
                                val sampleTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outBufIndex = decoder.dequeueOutputBuffer(info, 10000)
                if (outBufIndex >= 0) {
                    val buf = decoder.getOutputBuffer(outBufIndex)
                    if (buf != null) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)

                        val shortBuf = buf.asShortBuffer()
                        val pcmLength = shortBuf.remaining()

                        for (i in 0 until pcmLength) {
                            val sample = shortBuf.get().toDouble()
                            blockEnergySum += sample * sample
                            sampleInBlockCount++

                            if (sampleInBlockCount >= blockSamples) {
                                val avgBlockEnergy = blockEnergySum / blockSamples
                                energyList.add(avgBlockEnergy)
                                blockEnergySum = 0.0
                                sampleInBlockCount = 0
                            }
                        }
                        totalSamplesRead += pcmLength
                    }
                    decoder.releaseOutputBuffer(outBufIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }
                }
            }

            if (energyList.isEmpty()) {
                return@withContext null
            }

            // High-pass filter & Peak finding algorithm using local averages
            val historySize = (1.0 * sampleRate / blockSamples).toInt().coerceAtLeast(20).coerceAtMost(60)
            val detectedBeatBlocks = ArrayList<Int>()

            for (i in historySize until (energyList.size - historySize)) {
                val currentEnergy = energyList[i]
                
                var localSum = 0.0
                for (j in (i - historySize) until i) {
                    localSum += energyList[j]
                }
                val localAvg = localSum / historySize

                val threshold = 1.3
                if (currentEnergy > threshold * localAvg && localAvg > 0) {
                    var isMax = true
                    for (k in -3..3) {
                        if (i + k in 0 until energyList.size) {
                            if (energyList[i + k] > currentEnergy) {
                                isMax = false
                                break
                            }
                        }
                    }

                    if (isMax) {
                        val lastBeat = detectedBeatBlocks.lastOrNull()
                        val blockDistance = if (lastBeat != null) i - lastBeat else 9999
                        val minBlockDistance = (0.28 * sampleRate / blockSamples).toInt().coerceAtLeast(10)
                        if (blockDistance >= minBlockDistance) {
                            detectedBeatBlocks.add(i)
                        }
                    }
                }
            }

            if (detectedBeatBlocks.size < 3) {
                return@withContext null
            }

            val intervalsInSec = ArrayList<Double>()
            val blockTime = blockSamples.toDouble() / sampleRate.toDouble()

            for (i in 1 until detectedBeatBlocks.size) {
                val diffInBlocks = detectedBeatBlocks[i] - detectedBeatBlocks[i - 1]
                val intervalSec = diffInBlocks * blockTime
                intervalsInSec.add(intervalSec)
            }

            val bpmCounts = HashMap<Int, Int>()
            for (interval in intervalsInSec) {
                val rawBpm = (60.0 / interval).toInt()
                if (rawBpm in 60..180) {
                    for (offset in -1..1) {
                        val targetBpm = rawBpm + offset
                        bpmCounts[targetBpm] = (bpmCounts[targetBpm] ?: 0) + 1
                    }
                }
            }

            val bestBpm = bpmCounts.maxByOrNull { it.value }?.key
            Log.d(TAG, "Successfully detected BPM: $bestBpm for $uriString")
            return@withContext bestBpm
        } catch (e: Exception) {
            Log.e(TAG, "Uncaught error during BPM offline calculation", e)
            null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (ignored: Exception) {}
            try {
                extractor?.release()
            } catch (ignored: Exception) {}
        }
    }
}
