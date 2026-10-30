package com.osei.djmixer.ui.analysis

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.osei.djmixer.data.MediaItemEntity
import com.osei.djmixer.data.MediaRepository
import com.osei.djmixer.data.TrackAnalysisEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Source of analysis metrics to ensure honest reporting without inventing data.
 */
enum class AnalysisSource {
    DSP,
    METADATA,
    UNKNOWN
}

/**
 * Result data class for real audio analysis.
 */
data class TrackAnalysisResult(
    val trackId: String,
    val bpm: Int?,             // null if UNKNOWN
    val bpmConfidence: Float,  // 0.0f..1.0f
    val bpmSource: AnalysisSource = AnalysisSource.UNKNOWN,
    val musicalKey: String?,   // e.g. "Am", "C", "F#m", or null if UNKNOWN
    val keyConfidence: Float,  // 0.0f..1.0f
    val keySource: AnalysisSource = AnalysisSource.UNKNOWN,
    val energyScore: Float?,   // null if UNKNOWN
    val energyConfidence: Float,
    val energyLevel: String,   // "LOW", "MEDIUM", "HIGH", or "UNKNOWN"
    val durationMs: Long
)

/**
 * Real DSP audio analyzer for Osei DJ Mixer.
 * Performs genuine BPM detection, 12-tone Chroma musical key estimation,
 * and multi-factor energy analysis from decoded PCM audio frames.
 */
object AudioTrackAnalyzer {
    private const val TAG = "AudioTrackAnalyzer"
    const val CURRENT_ANALYSIS_VERSION = 3

    /**
     * Parse and map source tracking string safely.
     * Explicitly recorded "DSP" remains DSP, "METADATA" remains METADATA.
     * Missing, empty, or unknown strings resolve strictly to UNKNOWN without fabricating origin.
     */
    fun parseAnalysisSource(source: String?): AnalysisSource {
        return when (source?.trim()?.uppercase()) {
            "DSP" -> AnalysisSource.DSP
            "METADATA" -> AnalysisSource.METADATA
            else -> AnalysisSource.UNKNOWN
        }
    }

    // 12 Pitch class labels
    val PITCH_CLASSES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Krumhansl-Kessler Tonal Key Profiles (12 values for root to major 7th)
    private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    suspend fun analyzeTrack(
        context: Context,
        media: MediaItemEntity,
        repository: MediaRepository? = null,
        forceReanalyze: Boolean = false
    ): TrackAnalysisResult = withContext(Dispatchers.Default) {
        val fileTimestamp = getFileTimestamp(context, media.uri)

        // Step 1: Check persistent database cache
        if (!forceReanalyze && repository != null) {
            try {
                val cached = repository.getTrackAnalysis(media.id)
                if (cached != null && cached.analysisVersion == CURRENT_ANALYSIS_VERSION) {
                    if (fileTimestamp == 0L || cached.fileLastModified >= fileTimestamp) {
                        val bpmSrc = parseAnalysisSource(cached.bpmSource)
                        val keySrc = parseAnalysisSource(cached.keySource)
                        return@withContext TrackAnalysisResult(
                            trackId = cached.trackId,
                            bpm = cached.bpm,
                            bpmConfidence = cached.bpmConfidence,
                            bpmSource = bpmSrc,
                            musicalKey = cached.musicalKey,
                            keyConfidence = cached.keyConfidence,
                            keySource = keySrc,
                            energyScore = cached.energyScore,
                            energyConfidence = cached.energyConfidence,
                            energyLevel = cached.energyLevel,
                            durationMs = cached.durationMs
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cache read failed, proceeding with fresh analysis", e)
            }
        }

        // Step 2: Extract real PCM audio samples (up to 25 seconds for fast analysis)
        val pcmData = extractPcmForAnalysis(context, media.uri)

        if (pcmData == null || pcmData.samples.isEmpty()) {
            // Cannot extract audio -> return honest UNKNOWN results without inventing metrics
            val hasMetaBpm = media.bpm != null && media.bpm > 0
            val unknownResult = TrackAnalysisResult(
                trackId = media.id,
                bpm = if (hasMetaBpm) media.bpm else null,
                bpmConfidence = if (hasMetaBpm) 0.6f else 0.0f,
                bpmSource = if (hasMetaBpm) AnalysisSource.METADATA else AnalysisSource.UNKNOWN,
                musicalKey = null,
                keyConfidence = 0.0f,
                keySource = AnalysisSource.UNKNOWN,
                energyScore = null,
                energyConfidence = 0.0f,
                energyLevel = "UNKNOWN",
                durationMs = media.duration
            )
            return@withContext unknownResult
        }

        // Step 3: Run Real BPM Detection
        val (detectedBpm, bpmConf) = analyzeBpm(pcmData.samples, pcmData.sampleRate)

        // Step 4: Run Real Musical Key Estimation
        val (detectedKey, keyConf) = analyzeMusicalKey(pcmData.samples, pcmData.sampleRate)

        // Step 5: Run Real Energy Analysis
        val (energyScore, energyConf, energyLevel) = analyzeEnergy(pcmData.samples, pcmData.sampleRate)

        val finalBpm: Int?
        val finalBpmConfidence: Float
        val finalBpmSource: AnalysisSource

        if (detectedBpm != null && detectedBpm > 0) {
            finalBpm = detectedBpm
            finalBpmConfidence = bpmConf
            finalBpmSource = AnalysisSource.DSP
        } else if (media.bpm != null && media.bpm > 0) {
            finalBpm = media.bpm
            finalBpmConfidence = 0.6f
            finalBpmSource = AnalysisSource.METADATA
        } else {
            finalBpm = null
            finalBpmConfidence = 0.0f
            finalBpmSource = AnalysisSource.UNKNOWN
        }

        val finalKey: String?
        val finalKeyConfidence: Float
        val finalKeySource: AnalysisSource

        if (detectedKey != null) {
            finalKey = detectedKey
            finalKeyConfidence = keyConf
            finalKeySource = AnalysisSource.DSP
        } else {
            finalKey = null
            finalKeyConfidence = 0.0f
            finalKeySource = AnalysisSource.UNKNOWN
        }

        val result = TrackAnalysisResult(
            trackId = media.id,
            bpm = finalBpm,
            bpmConfidence = finalBpmConfidence,
            bpmSource = finalBpmSource,
            musicalKey = finalKey,
            keyConfidence = finalKeyConfidence,
            keySource = finalKeySource,
            energyScore = energyScore,
            energyConfidence = energyConf,
            energyLevel = energyLevel,
            durationMs = if (media.duration > 0) media.duration else (pcmData.samples.size * 1000L / pcmData.sampleRate)
        )

        // Step 6: Persist in database cache
        if (repository != null) {
            try {
                val entity = TrackAnalysisEntity(
                    trackId = media.id,
                    fileLastModified = fileTimestamp,
                    durationMs = result.durationMs,
                    bpm = result.bpm,
                    bpmConfidence = result.bpmConfidence,
                    bpmSource = result.bpmSource.name,
                    musicalKey = result.musicalKey,
                    keyConfidence = result.keyConfidence,
                    keySource = result.keySource.name,
                    energyScore = result.energyScore,
                    energyConfidence = result.energyConfidence,
                    energyLevel = result.energyLevel,
                    analysisVersion = CURRENT_ANALYSIS_VERSION
                )
                repository.saveTrackAnalysis(entity)
                if (result.bpm != null && result.bpm > 0 && media.bpm != result.bpm) {
                    repository.updateMediaBpm(media.id, result.bpm)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cache save failed", e)
            }
        }

        return@withContext result
    }

    private class PcmExtractionResult(
        val samples: ShortArray,
        val sampleRate: Int
    )

    private fun extractPcmForAnalysis(context: Context, uriString: String): PcmExtractionResult? {
        val uri = Uri.parse(uriString)

        // Fast path for WAV: read PCM directly from file/stream without MediaCodec
        if (uriString.lowercase().endsWith(".wav") || uriString.lowercase().contains(".wav?")) {
            val wavPcm = readWavPcm(context, uri, maxSeconds = 25)
            if (wavPcm != null) return wavPcm
        }

        // Standard path: MediaExtractor + MediaCodec decoding up to 25 seconds
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

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

            if (trackIndex == -1 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            } else 44100

            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 2

            val maxSamples = sampleRate * 25 // 25 seconds of mono-downmixed audio
            val collected = ArrayList<Short>(maxSamples)
            val info = MediaCodec.BufferInfo()

            var isExtractorEOS = false
            var isDecoderEOS = false
            var iterations = 0
            val maxIterations = 4000

            while (!isDecoderEOS && collected.size < maxSamples && iterations < maxIterations) {
                iterations++
                if (!isExtractorEOS) {
                    val inIdx = decoder.dequeueInputBuffer(4000)
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

                val outIdx = decoder.dequeueOutputBuffer(info, 4000)
                if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.asShortBuffer()
                        while (shortBuf.hasRemaining() && collected.size < maxSamples) {
                            if (channels == 2 && shortBuf.remaining() >= 2) {
                                val left = shortBuf.get().toInt()
                                val right = shortBuf.get().toInt()
                                collected.add(((left + right) / 2).toShort())
                            } else {
                                collected.add(shortBuf.get())
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }
                }
            }

            if (collected.isEmpty()) return null
            return PcmExtractionResult(collected.toShortArray(), sampleRate)
        } catch (e: Exception) {
            Log.w(TAG, "PCM extraction failed for $uriString: ${e.message}")
            return null
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }
    }

    private fun readWavPcm(context: Context, uri: Uri, maxSeconds: Int): PcmExtractionResult? {
        var isStream: InputStream? = null
        try {
            isStream = context.contentResolver.openInputStream(uri) ?: return null
            val header = ByteArray(44)
            val readHeader = isStream.read(header)
            if (readHeader < 44) return null

            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") return null

            val channels = bb.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = bb.getInt(24).coerceAtLeast(8000)
            val bitsPerSample = bb.getShort(34).toInt().coerceAtLeast(8)
            val dataSize = bb.getInt(40).toLong().and(0xFFFFFFFFL)

            val maxSamplesToRead = (sampleRate * maxSeconds).coerceAtLeast(44100)
            val collected = ShortArray(maxSamplesToRead)
            var samplesRead = 0

            val buffer = ByteArray(8192)
            while (samplesRead < maxSamplesToRead) {
                val bytesRead = isStream.read(buffer)
                if (bytesRead <= 0) break
                val shortsInBuf = bytesRead / 2
                val shortBuf = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (shortBuf.hasRemaining() && samplesRead < maxSamplesToRead) {
                    if (channels == 2 && shortBuf.remaining() >= 2) {
                        val left = shortBuf.get().toInt()
                        val right = shortBuf.get().toInt()
                        collected[samplesRead++] = ((left + right) / 2).toShort()
                    } else {
                        collected[samplesRead++] = shortBuf.get()
                    }
                }
            }

            if (samplesRead == 0) return null
            return PcmExtractionResult(collected.copyOf(samplesRead), sampleRate)
        } catch (e: Exception) {
            return null
        } finally {
            try { isStream?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Real BPM Detection with confidence estimation.
     * Uses sub-band onset flux and inter-beat interval clustering.
     */
    fun analyzeBpm(samples: ShortArray, sampleRate: Int): Pair<Int?, Float> {
        if (samples.size < sampleRate * 3) {
            return Pair(null, 0.0f) // Audio too short for reliable BPM
        }

        // Window size ~23ms (1024 samples at 44.1kHz)
        val windowSize = 1024
        val numWindows = samples.size / windowSize
        if (numWindows < 30) return Pair(null, 0.0f)

        // Compute energy envelope
        val energyEnvelope = DoubleArray(numWindows)
        for (w in 0 until numWindows) {
            var sum = 0.0
            val offset = w * windowSize
            for (i in 0 until windowSize) {
                val s = samples[offset + i].toDouble()
                sum += s * s
            }
            energyEnvelope[w] = sqrt(sum / windowSize)
        }

        // Onset Detection Function (first-order spectral flux / difference)
        val onsets = DoubleArray(numWindows)
        for (i in 1 until numWindows) {
            val diff = energyEnvelope[i] - energyEnvelope[i - 1]
            onsets[i] = if (diff > 0) diff else 0.0
        }

        // Local dynamic threshold peak picking
        val historySize = 16
        val beatIndices = ArrayList<Int>()
        for (i in historySize until (numWindows - historySize)) {
            var localSum = 0.0
            for (j in (i - historySize) until (i + historySize)) {
                localSum += onsets[j]
            }
            val localAvg = localSum / (historySize * 2)
            val current = onsets[i]
            if (current > localAvg * 1.35 && current > 200.0) {
                // Check if local maximum
                var isMax = true
                for (k in -2..2) {
                    if (k != 0 && onsets[i + k] > current) {
                        isMax = false
                        break
                    }
                }
                if (isMax) {
                    val last = beatIndices.lastOrNull()
                    // Minimum interval ~0.26s (equivalent to max 230 BPM)
                    val minDistance = (0.26 * sampleRate / windowSize).toInt().coerceAtLeast(8)
                    if (last == null || (i - last) >= minDistance) {
                        beatIndices.add(i)
                    }
                }
            }
        }

        if (beatIndices.size < 6) {
            return Pair(null, 0.0f) // Not enough regular beats found
        }

        // Calculate inter-beat intervals in seconds
        val windowSec = windowSize.toDouble() / sampleRate.toDouble()
        val intervals = ArrayList<Double>()
        for (i in 1 until beatIndices.size) {
            val delta = (beatIndices[i] - beatIndices[i - 1]) * windowSec
            if (delta in 0.30..1.20) { // 50 BPM to 200 BPM
                intervals.add(delta)
            }
        }

        if (intervals.size < 4) {
            return Pair(null, 0.0f)
        }

        // Interval histogram
        val bpmHistogram = HashMap<Int, Int>()
        for (interval in intervals) {
            val rawBpm = (60.0 / interval).roundToInt()
            if (rawBpm in 60..185) {
                for (offset in -1..1) {
                    val b = rawBpm + offset
                    bpmHistogram[b] = (bpmHistogram[b] ?: 0) + 1
                }
            }
        }

        val bestEntry = bpmHistogram.maxByOrNull { it.value }
        if (bestEntry == null || bestEntry.value < 3) {
            return Pair(null, 0.0f)
        }

        val detectedBpm = bestEntry.key
        // Calculate confidence based on cluster concentration
        val totalClusterSamples = bpmHistogram[detectedBpm] ?: 0
        val confidence = (totalClusterSamples.toFloat() / (intervals.size * 2f)).coerceIn(0.2f, 0.95f)

        if (confidence < 0.35f) {
            return Pair(null, 0.0f) // Low confidence -> return UNKNOWN
        }

        return Pair(detectedBpm, confidence)
    }

    /**
     * Real Musical Key Estimation using 12-Tone Pitch Class Profile (Chroma)
     * and Krumhansl-Schmuckler Key Profile correlation across all 24 Major & Minor keys.
     */
    fun analyzeMusicalKey(samples: ShortArray, sampleRate: Int): Pair<String?, Float> {
        if (samples.size < sampleRate * 2) {
            return Pair(null, 0.0f)
        }

        // 12-Bin Chroma Accumulator: C, C#, D, D#, E, F, F#, G, G#, A, A#, B
        val chroma = DoubleArray(12)

        // Analyze frequency bins via Goertzel filter for semitone center frequencies
        // Range: C2 (65.4Hz) to B5 (987.8Hz) across 4 octaves
        val a4 = 440.0
        val baseFreqs = DoubleArray(12)
        for (i in 0 until 12) {
            // Formula: f = 440 * 2^((i - 9) / 12)
            baseFreqs[i] = a4 * 2.0.pow((i - 9) / 12.0)
        }

        // Sample blocks
        val blockSize = 2048
        val numBlocks = (samples.size / blockSize).coerceAtMost(100)

        for (b in 0 until numBlocks) {
            val offset = b * blockSize
            for (octave in 1..4) {
                val multiplier = 2.0.pow(octave - 3) // octave 1 = *0.25, 2 = *0.5, 3 = *1.0, 4 = *2.0
                for (pitchClass in 0 until 12) {
                    val targetFreq = baseFreqs[pitchClass] * multiplier
                    val energy = computeGoertzelEnergy(samples, offset, blockSize, targetFreq, sampleRate)
                    chroma[pitchClass] += energy
                }
            }
        }

        // Normalize Chroma
        val chromaSum = chroma.sum()
        if (chromaSum <= 1e-6) {
            return Pair(null, 0.0f)
        }
        for (i in 0 until 12) {
            chroma[i] /= chromaSum
        }

        // Correlate with 12 Major and 12 Minor Krumhansl-Schmuckler Profiles
        var bestKey: String? = null
        var maxCorrelation = -1.0
        var secondMaxCorrelation = -1.0

        for (shift in 0 until 12) {
            // Major key correlation
            val rotatedMajor = rotateProfile(MAJOR_PROFILE, shift)
            val rMajor = pearsonCorrelation(chroma, rotatedMajor)
            if (rMajor > maxCorrelation) {
                secondMaxCorrelation = maxCorrelation
                maxCorrelation = rMajor
                bestKey = PITCH_CLASSES[shift] // e.g. "C", "D"
            } else if (rMajor > secondMaxCorrelation) {
                secondMaxCorrelation = rMajor
            }

            // Minor key correlation
            val rotatedMinor = rotateProfile(MINOR_PROFILE, shift)
            val rMinor = pearsonCorrelation(chroma, rotatedMinor)
            if (rMinor > maxCorrelation) {
                secondMaxCorrelation = maxCorrelation
                maxCorrelation = rMinor
                bestKey = "${PITCH_CLASSES[shift]}m" // e.g. "Am", "F#m"
            } else if (rMinor > secondMaxCorrelation) {
                secondMaxCorrelation = rMinor
            }
        }

        // Margin of confidence
        val margin = (maxCorrelation - secondMaxCorrelation).coerceAtLeast(0.0)
        val confidence = (maxCorrelation * 0.6 + margin * 3.0).toFloat().coerceIn(0.0f, 1.0f)

        if (maxCorrelation < 0.45 || confidence < 0.38f || bestKey == null) {
            return Pair(null, 0.0f) // Insufficient confidence -> return UNKNOWN
        }

        return Pair(bestKey, confidence)
    }

    private fun rotateProfile(profile: DoubleArray, shift: Int): DoubleArray {
        val out = DoubleArray(12)
        for (i in 0 until 12) {
            out[(i + shift) % 12] = profile[i]
        }
        return out
    }

    private fun pearsonCorrelation(x: DoubleArray, y: DoubleArray): Double {
        val n = x.size
        var meanX = 0.0
        var meanY = 0.0
        for (i in 0 until n) {
            meanX += x[i]
            meanY += y[i]
        }
        meanX /= n
        meanY /= n

        var num = 0.0
        var denX = 0.0
        var denY = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        val den = sqrt(denX * denY)
        return if (den > 1e-9) num / den else 0.0
    }

    private fun computeGoertzelEnergy(
        samples: ShortArray,
        offset: Int,
        numSamples: Int,
        targetFreq: Double,
        sampleRate: Int
    ): Double {
        val k = (0.5 + (numSamples * targetFreq) / sampleRate).toInt()
        val omega = (2.0 * PI * k) / numSamples
        val cosine = cos(omega)
        val coeff = 2.0 * cosine

        var q0: Double
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until numSamples) {
            val sample = samples[offset + i].toDouble()
            q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }

        return q1 * q1 + q2 * q2 - q1 * q2 * coeff
    }

    /**
     * Real Energy Analysis.
     * Computes RMS loudness, peak level, dynamic range, and high-frequency content.
     */
    fun analyzeEnergy(samples: ShortArray, sampleRate: Int): Triple<Float?, Float, String> {
        if (samples.isEmpty()) {
            return Triple(null, 0.0f, "UNKNOWN")
        }

        var sumSq = 0.0
        var maxPeak = 0
        var highFreqDiffSum = 0.0

        for (i in samples.indices) {
            val s = abs(samples[i].toInt())
            if (s > maxPeak) maxPeak = s
            val sd = samples[i].toDouble()
            sumSq += sd * sd

            if (i > 0) {
                // High frequency / transient detection
                val diff = abs(samples[i] - samples[i - 1]).toDouble()
                highFreqDiffSum += diff
            }
        }

        val count = samples.size.toDouble()
        if (count <= 0.0) return Triple(null, 0.0f, "UNKNOWN")

        val rms = (sqrt(sumSq / count) / 32768.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
        val peak = (maxPeak / 32768.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it.coerceIn(0.0, 1.0) }
        val highFreqRatio = (highFreqDiffSum / (count * 32768.0)).let { if (it.isNaN() || it.isInfinite()) 0.0 else it.coerceIn(0.0, 1.0) }

        // Normalized Energy Score: weights RMS (loudness), Peak, and high-frequency punch
        val rawScore = (rms * 3.8 * 0.50) + (peak * 0.30) + (highFreqRatio * 4.0 * 0.20)
        if (rawScore.isNaN() || rawScore.isInfinite()) {
            return Triple(null, 0.0f, "UNKNOWN")
        }
        val energyScore = rawScore.toFloat().coerceIn(0.05f, 0.98f)

        val classification = when {
            energyScore < 0.40f -> "LOW"
            energyScore < 0.72f -> "MEDIUM"
            else -> "HIGH"
        }

        val confidence = if (count >= sampleRate * 5) 0.85f else 0.50f
        return Triple(energyScore, confidence, classification)
    }

    // ==========================================================
    // HARMONIC KEY / CAMELOT WHEEL SYSTEM
    // ==========================================================

    /**
     * Converts standard musical key (e.g. "Am", "C", "F#m", "Db") to Camelot Wheel notation (e.g. "8A", "8B", "11A").
     */
    fun keyToCamelot(key: String?): String? {
        if (key.isNullOrBlank() || key.equals("UNKNOWN", ignoreCase = true)) return null
        val clean = key.trim()
        return when (clean) {
            // Minor keys (Suffix 'm' -> Camelot 'A')
            "Abm", "G#m" -> "1A"
            "Ebm", "D#m" -> "2A"
            "Bbm", "A#m" -> "3A"
            "Fm" -> "4A"
            "Cm" -> "5A"
            "Gm" -> "6A"
            "Dm" -> "7A"
            "Am" -> "8A"
            "Em" -> "9A"
            "Bm" -> "10A"
            "F#m", "Gbm" -> "11A"
            "Dbm", "C#m" -> "12A"

            // Major keys (No 'm' suffix -> Camelot 'B')
            "B" -> "1B"
            "F#", "Gb" -> "2B"
            "Db", "C#" -> "3B"
            "Ab", "G#" -> "4B"
            "Eb", "D#" -> "5B"
            "Bb", "A#" -> "6B"
            "F" -> "7B"
            "C" -> "8B"
            "G" -> "9B"
            "D" -> "10B"
            "A" -> "11B"
            "E" -> "12B"
            else -> null
        }
    }

    enum class HarmonicRelation(val score: Float, val label: String) {
        EXACT(1.0f, "Exact Key Match"),
        RELATIVE(0.92f, "Relative Major/Minor"),
        ADJACENT(0.88f, "Adjacent Key (Harmonic)"),
        ENERGY_BOOST(0.76f, "Energy Boost (+2)"),
        DOMINANT(0.70f, "Fifth / Dominant"),
        CROSS_MODAL(0.65f, "Diagonal Harmonic Step"),
        UNKNOWN(0.50f, "Key Undetected"),
        CLASHING(0.20f, "Distant Key")
    }

    /**
     * Evaluates harmonic compatibility between two musical keys using the Camelot wheel.
     */
    fun evaluateHarmonicCompatibility(keyA: String?, keyB: String?): Pair<Float, HarmonicRelation> {
        val camA = keyToCamelot(keyA)
        val camB = keyToCamelot(keyB)

        if (camA == null || camB == null) {
            return Pair(HarmonicRelation.UNKNOWN.score, HarmonicRelation.UNKNOWN)
        }

        val numA = camA.dropLast(1).toIntOrNull() ?: return Pair(HarmonicRelation.UNKNOWN.score, HarmonicRelation.UNKNOWN)
        val letA = camA.last()
        val numB = camB.dropLast(1).toIntOrNull() ?: return Pair(HarmonicRelation.UNKNOWN.score, HarmonicRelation.UNKNOWN)
        val letB = camB.last()

        // Same exact key (e.g. 8A -> 8A)
        if (numA == numB && letA == letB) {
            return Pair(HarmonicRelation.EXACT.score, HarmonicRelation.EXACT)
        }

        // Relative Major / Minor (e.g. 8A <-> 8B)
        if (numA == numB && letA != letB) {
            return Pair(HarmonicRelation.RELATIVE.score, HarmonicRelation.RELATIVE)
        }

        // Circular distance on 12-hour Camelot wheel
        val diff = abs(numA - numB)
        val circularDiff = min(diff, 12 - diff)

        if (letA == letB) {
            if (circularDiff == 1) {
                return Pair(HarmonicRelation.ADJACENT.score, HarmonicRelation.ADJACENT)
            }
            // Energy boost: +2 semitones or +2 Camelot hours clockwise
            if ((numB - numA + 12) % 12 == 2) {
                return Pair(HarmonicRelation.ENERGY_BOOST.score, HarmonicRelation.ENERGY_BOOST)
            }
            // Dominant: +1 fifth clockwise (+7 semitones)
            if ((numB - numA + 12) % 12 == 7) {
                return Pair(HarmonicRelation.DOMINANT.score, HarmonicRelation.DOMINANT)
            }
        } else {
            // Diagonal step (+/- 1 number with mode change)
            if (circularDiff == 1) {
                return Pair(HarmonicRelation.CROSS_MODAL.score, HarmonicRelation.CROSS_MODAL)
            }
        }

        return Pair(HarmonicRelation.CLASHING.score, HarmonicRelation.CLASHING)
    }

    // ==========================================================
    // BPM COMPATIBILITY SYSTEM
    // ==========================================================

    /**
     * Evaluates tempo compatibility between two tracks.
     */
    fun evaluateBpmCompatibility(bpmA: Int?, bpmB: Int?): Pair<Float, String> {
        if (bpmA == null || bpmB == null || bpmA <= 0 || bpmB <= 0) {
            return Pair(0.50f, "BPM Undetected (Neutral)")
        }

        val diff = abs(bpmA - bpmB).toFloat() / bpmA.toFloat()
        if (diff <= 0.02f) return Pair(1.0f, "Exact BPM ($bpmB)")
        if (diff <= 0.05f) return Pair(0.92f, "Close BPM (±${(diff * 100).roundToInt()}%)")
        if (diff <= 0.08f) return Pair(0.80f, "Syncable (±${(diff * 100).roundToInt()}%)")
        if (diff <= 0.12f) return Pair(0.65f, "Pitch Bend Range (±${(diff * 100).roundToInt()}%)")

        // Half-time / Double-time detection (e.g. 70 <-> 140 BPM)
        val halfDiff = abs(bpmA - bpmB * 2).toFloat() / bpmA.toFloat()
        val doubleDiff = abs(bpmA * 2 - bpmB).toFloat() / (bpmA * 2).toFloat()
        if (halfDiff <= 0.04f) return Pair(0.86f, "Half-Time Sync ($bpmB BPM)")
        if (doubleDiff <= 0.04f) return Pair(0.86f, "Double-Time Sync ($bpmB BPM)")

        val penalty = (1.0f - diff * 1.5f).coerceIn(0.10f, 0.50f)
        return Pair(penalty, "Tempo Shift (Δ${abs(bpmA - bpmB)} BPM)")
    }

    // ==========================================================
    // ENERGY COMPATIBILITY SYSTEM
    // ==========================================================

    enum class AutoDjMixMode(val title: String, val subtitle: String) {
        SMOOTH_FLOW("Smooth Flow", "Matches energy level for seamless progression"),
        BUILD_ENERGY("Build Energy", "Progressively steps up energy for peak dancefloor excitement"),
        PEAK_HOUR("Peak Hour", "Prioritizes high-energy bangers"),
        CHILL_OUT("Chill Lounge", "Maintains relaxed, lower-energy vibes")
    }

    /**
     * Evaluates energy progression between two tracks based on selected mixing mode.
     */
    fun evaluateEnergyCompatibility(energyA: Float?, energyB: Float?, mode: AutoDjMixMode): Pair<Float, String> {
        if (energyA == null || energyB == null) {
            return Pair(0.50f, "Energy Unknown (Neutral)")
        }
        val eA = energyA
        val eB = energyB

        return when (mode) {
            AutoDjMixMode.SMOOTH_FLOW -> {
                val delta = abs(eA - eB)
                val score = (1.0f - delta * 1.3f).coerceIn(0.2f, 1.0f)
                val desc = if (delta <= 0.15f) "Even Energy Flow" else "Energy Shift (Δ${(delta * 100).roundToInt()}%)"
                Pair(score, desc)
            }
            AutoDjMixMode.BUILD_ENERGY -> {
                val diff = eB - eA
                val score = when {
                    diff in 0.05f..0.30f -> 1.0f
                    diff > 0.30f -> 0.85f
                    diff in -0.05f..0.05f -> 0.80f
                    else -> 0.40f
                }
                val desc = if (diff > 0) "Energy Step-Up (+${(diff * 100).roundToInt()}%)" else "Energy Hold"
                Pair(score, desc)
            }
            AutoDjMixMode.PEAK_HOUR -> {
                val score = (eB * 1.1f).coerceIn(0.2f, 1.0f)
                val desc = if (eB >= 0.70f) "Peak High Energy" else "Moderate Energy"
                Pair(score, desc)
            }
            AutoDjMixMode.CHILL_OUT -> {
                val score = (1.1f - eB).coerceIn(0.2f, 1.0f)
                val desc = if (eB <= 0.45f) "Deep Chill Energy" else "Gentle Energy"
                Pair(score, desc)
            }
        }
    }

    // ==========================================================
    // INTELLIGENT TRANSITION SCORING ENGINE
    // ==========================================================

    data class TransitionScore(
        val totalScore: Float, // 0.0f..1.0f
        val matchPercentage: Int, // 0..100%
        val bpmScore: Float,
        val bpmDescription: String,
        val keyScore: Float,
        val harmonicRelation: HarmonicRelation,
        val energyScore: Float,
        val energyDescription: String,
        val camelotCurrent: String?,
        val camelotCandidate: String?,
        val summary: String
    )

    /**
     * Calculates the composite transition compatibility score combining
     * Harmonic Key (38%), BPM Tempo (38%), and Energy Progression (24%).
     * If energy is UNKNOWN, its contribution is neutralized so valid BPM and harmonic key
     * determine the compatibility without penalizing or falsifying the score.
     */
    fun calculateTransitionScore(
        current: TrackAnalysisResult?,
        candidate: TrackAnalysisResult?,
        mode: AutoDjMixMode = AutoDjMixMode.SMOOTH_FLOW
    ): TransitionScore {
        val (bpmScore, bpmDesc) = evaluateBpmCompatibility(current?.bpm, candidate?.bpm)
        val (keyScore, harmonicRelation) = evaluateHarmonicCompatibility(current?.musicalKey, candidate?.musicalKey)
        val (energyScore, energyDesc) = evaluateEnergyCompatibility(current?.energyScore, candidate?.energyScore, mode)

        val hasEnergy = current?.energyScore != null && candidate?.energyScore != null
        val totalScore = if (hasEnergy) {
            (bpmScore * 0.38f + keyScore * 0.38f + energyScore * 0.24f).coerceIn(0.0f, 1.0f)
        } else {
            // Neutralize energy when unknown so valid BPM and Key are weighted equally
            (bpmScore * 0.50f + keyScore * 0.50f).coerceIn(0.0f, 1.0f)
        }
        val matchPercentage = (totalScore * 100).roundToInt()

        val camCurrent = keyToCamelot(current?.musicalKey)
        val camCandidate = keyToCamelot(candidate?.musicalKey)

        val keyDetails = when {
            camCurrent != null && camCandidate != null -> "$camCurrent → $camCandidate (${harmonicRelation.label})"
            candidate?.musicalKey != null -> "${candidate.musicalKey} (${harmonicRelation.label})"
            else -> harmonicRelation.label
        }

        val summary = "$matchPercentage% Match • $keyDetails • $bpmDesc"

        return TransitionScore(
            totalScore = totalScore,
            matchPercentage = matchPercentage,
            bpmScore = bpmScore,
            bpmDescription = bpmDesc,
            keyScore = keyScore,
            harmonicRelation = harmonicRelation,
            energyScore = energyScore,
            energyDescription = energyDesc,
            camelotCurrent = camCurrent,
            camelotCandidate = camCandidate,
            summary = summary
        )
    }

    private fun getFileTimestamp(context: Context, uriString: String): Long {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                File(uri.path ?: "").lastModified()
            } else {
                val f = File(uriString)
                if (f.exists()) f.lastModified() else 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
