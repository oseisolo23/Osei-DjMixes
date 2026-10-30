package com.osei.djmixer.ui

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * A comprehensive DJ Audio Processor handling:
 * - Real BPM Detection
 * - 3-Band EQ (Low Shelf, Mid Peaking, High Shelf)
 * - True DJ Filter (Low-Pass Filter < 0.45, Neutral Bypass 0.45..0.55, High-Pass Filter > 0.55)
 * - Master Gain & Volume
 * - Real DSP DJ Effects (Echo, Reverb, Flanger, Bit Crusher, Roll / Stutter, Bass Boost)
 * - Real-time Audio Waveform History
 */
class DjAudioProcessor : AudioProcessor {

    private val _bpm = MutableStateFlow(0)
    val bpm = _bpm.asStateFlow()

    var isAutoBpmEnabled = true

    private val _waveformHistory = MutableStateFlow(FloatArray(100))
    val waveformHistory = _waveformHistory.asStateFlow()
    private val localWaveform = FloatArray(100)

    fun setBpm(value: Int) {
        isAutoBpmEnabled = false
        _bpm.value = value
        intervals.clear()
        val sr = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100
        lastBeatTimestamp = (totalFramesProcessed * 1000) / sr
    }

    private var inputAudioFormat = AudioFormat.NOT_SET

    // EQ and Filter Parameters
    var lowGain = 1.0f  // 0.0 to 2.0 (1.0 = flat)
    var midGain = 1.0f  // 0.0 to 2.0 (1.0 = flat)
    var highGain = 1.0f // 0.0 to 2.0 (1.0 = flat)
    var filterValue = 0.5f // 0.0 = LPF, 0.5 = Bypass, 1.0 = HPF
    var masterGain = 1.0f

    // Real DSP Effects
    var echoEnabled = false
    var reverbEnabled = false
    var flangerEnabled = false
    var bitCrusherEnabled = false
    var rollEnabled = false
    var bassBoostEnabled = false
    var brakeEnabled = false

    // Delay Buffers
    private var echoBuffer = DoubleArray(44100 * 2)
    private var echoIndex = 0

    private var reverbBuffer = DoubleArray(44100 * 2)
    private var reverbIndex = 0

    private var flangerBuffer = DoubleArray(44100)
    private var flangerIndex = 0
    private var flangerPhase = 0.0

    private var rollBuffer = DoubleArray(8820) // ~100ms at 44.1kHz stereo
    private var rollIndex = 0
    private var isRollCapturing = true

    private var crushHoldSample = 0.0
    private var sampleDecimator = 0

    private var brakeMultiplier = 1.0

    // BPM Constants
    private val HISTORY_SIZE = 43
    private val energyHistory = mutableListOf<Double>()
    private var lastBeatTimestamp = 0L
    private val intervals = mutableListOf<Long>()

    // Biquad states for stereo
    private var lowState = Array(2) { BiquadState() }
    private var midState = Array(2) { BiquadState() }
    private var highState = Array(2) { BiquadState() }
    private var filterState = Array(2) { BiquadState() }

    // Biquad Coefficients
    private var lowCoeffs = BiquadCoeffs()
    private var midCoeffs = BiquadCoeffs()
    private var highCoeffs = BiquadCoeffs()
    private var filterCoeffs = BiquadCoeffs()

    var onBufferProcessed: ((ByteBuffer, AudioFormat) -> Unit)? = null

    class BiquadState {
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
    }

    class BiquadCoeffs {
        var a0 = 1.0; var a1 = 0.0; var a2 = 0.0
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0
    }

    private fun calculateCoeffs() {
        val fs = inputAudioFormat.sampleRate.toDouble()
        if (fs <= 0) return

        // Low Shelf (300Hz)
        lowCoeffs = calculateShelfCoeffs(300.0, fs, lowGain.toDouble(), isLowShelf = true)
        // Mid Peaking (1000Hz)
        midCoeffs = calculatePeakingCoeffs(1000.0, fs, midGain.toDouble(), 0.7)
        // High Shelf (4000Hz)
        highCoeffs = calculateShelfCoeffs(4000.0, fs, highGain.toDouble(), isLowShelf = false)

        // DJ Filter (LPF on left, Bypass in center, HPF on right)
        filterCoeffs = if (filterValue < 0.45f) {
            // LPF: 40Hz to 18000Hz
            val freq = (40.0 + (filterValue / 0.45) * 17960.0).coerceIn(40.0, fs / 2.1)
            calculateLpfHpfCoeffs(freq, fs, isLpf = true)
        } else if (filterValue > 0.55f) {
            // HPF: 40Hz to 16000Hz
            val freq = (40.0 + ((filterValue - 0.55) / 0.45) * 15960.0).coerceIn(40.0, fs / 2.1)
            calculateLpfHpfCoeffs(freq, fs, isLpf = false)
        } else {
            BiquadCoeffs() // Center neutral bypass
        }
    }

    private fun calculateShelfCoeffs(f0: Double, fs: Double, gain: Double, isLowShelf: Boolean): BiquadCoeffs {
        val db = if (gain <= 0.01) -40.0 else (gain - 1.0) * 14.0
        val a = 10.0.pow(db / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val cosW0 = cos(w0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val coeffs = BiquadCoeffs()
        if (isLowShelf) {
            coeffs.b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
            coeffs.b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
            coeffs.b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
            coeffs.a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
            coeffs.a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
            coeffs.a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha
        } else {
            coeffs.b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
            coeffs.b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
            coeffs.b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
            coeffs.a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
            coeffs.a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
            coeffs.a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha
        }
        return coeffs
    }

    private fun calculatePeakingCoeffs(f0: Double, fs: Double, gain: Double, q: Double): BiquadCoeffs {
        val db = (gain - 1.0) * 14.0
        val a = 10.0.pow(db / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val alpha = sin(w0) / (2.0 * q)
        val cosW0 = cos(w0)

        val coeffs = BiquadCoeffs()
        coeffs.b0 = 1.0 + alpha * a
        coeffs.b1 = -2.0 * cosW0
        coeffs.b2 = 1.0 - alpha * a
        coeffs.a0 = 1.0 + alpha / a
        coeffs.a1 = -2.0 * cosW0
        coeffs.a2 = 1.0 - alpha / a
        return coeffs
    }

    private fun calculateLpfHpfCoeffs(f0: Double, fs: Double, isLpf: Boolean): BiquadCoeffs {
        val w0 = 2.0 * PI * f0 / fs
        val alpha = sin(w0) / (2.0 * 0.707)
        val cosW0 = cos(w0)

        val coeffs = BiquadCoeffs()
        if (isLpf) {
            coeffs.b0 = (1.0 - cosW0) / 2.0
            coeffs.b1 = 1.0 - cosW0
            coeffs.b2 = (1.0 - cosW0) / 2.0
            coeffs.a0 = 1.0 + alpha
            coeffs.a1 = -2.0 * cosW0
            coeffs.a2 = 1.0 - alpha
        } else {
            coeffs.b0 = (1.0 + cosW0) / 2.0
            coeffs.b1 = -(1.0 + cosW0)
            coeffs.b2 = (1.0 + cosW0) / 2.0
            coeffs.a0 = 1.0 + alpha
            coeffs.a1 = -2.0 * cosW0
            coeffs.a2 = 1.0 - alpha
        }
        return coeffs
    }

    private fun processSample(sample: Double, coeffs: BiquadCoeffs, state: BiquadState): Double {
        val output = (coeffs.b0 / coeffs.a0) * sample +
                (coeffs.b1 / coeffs.a0) * state.x1 +
                (coeffs.b2 / coeffs.a0) * state.x2 -
                (coeffs.a1 / coeffs.a0) * state.y1 -
                (coeffs.a2 / coeffs.a0) * state.y2

        state.x2 = state.x1
        state.x1 = sample
        state.y2 = state.y1
        state.y1 = output
        return output
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        this.inputAudioFormat = inputAudioFormat
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        lowState = Array(channels) { BiquadState() }
        midState = Array(channels) { BiquadState() }
        highState = Array(channels) { BiquadState() }
        filterState = Array(channels) { BiquadState() }

        val maxBufferSize = (inputAudioFormat.sampleRate * 2).coerceAtLeast(88200)
        if (echoBuffer.size < maxBufferSize) echoBuffer = DoubleArray(maxBufferSize)
        if (reverbBuffer.size < maxBufferSize) reverbBuffer = DoubleArray(maxBufferSize)
        echoBuffer.fill(0.0)
        reverbBuffer.fill(0.0)
        echoIndex = 0
        reverbIndex = 0

        flangerBuffer = DoubleArray((inputAudioFormat.sampleRate * 0.02).toInt().coerceAtLeast(1000))
        flangerBuffer.fill(0.0)
        flangerIndex = 0
        flangerPhase = 0.0

        rollBuffer = DoubleArray((inputAudioFormat.sampleRate * 0.15).toInt().coerceAtLeast(2048))
        rollBuffer.fill(0.0)
        rollIndex = 0
        isRollCapturing = true

        calculateCoeffs()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputAudioFormat != AudioFormat.NOT_SET

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var totalFramesProcessed: Long = 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        calculateCoeffs()

        val inputSize = inputBuffer.remaining()
        if (buffer.capacity() < inputSize) {
            buffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        val shortIn = inputBuffer.asShortBuffer()
        val shortOut = buffer.asShortBuffer()

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleCount = inputSize / 2
        totalFramesProcessed += (sampleCount / channels)

        var instantEnergy = 0.0
        var maxVal = 0.0

        val sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100
        val echoDelaySamples = ((280.0 / 1000.0) * sampleRate).toInt() * channels
        val reverbDelaySamples = ((45.0 / 1000.0) * sampleRate).toInt() * channels

        for (i in 0 until (sampleCount / channels)) {
            // Flanger LFO update per frame
            flangerPhase += (2.0 * PI * 0.4) / sampleRate
            val flangerModDelay = ((0.001 + (sin(flangerPhase) + 1.0) * 0.002) * sampleRate).toInt()

            // Turntable brake calculation
            if (brakeEnabled) {
                brakeMultiplier = (brakeMultiplier - 0.00008).coerceAtLeast(0.0)
            } else {
                brakeMultiplier = (brakeMultiplier + 0.001).coerceAtMost(1.0)
            }

            for (c in 0 until channels) {
                if (!shortIn.hasRemaining()) break
                val inputSample = shortIn.get().toDouble()

                var processed = inputSample

                // Roll / Beat Repeat Effect
                if (rollEnabled) {
                    if (isRollCapturing) {
                        rollBuffer[rollIndex] = inputSample
                        rollIndex = (rollIndex + 1) % rollBuffer.size
                        if (rollIndex == 0) isRollCapturing = false
                    } else {
                        processed = rollBuffer[rollIndex]
                        rollIndex = (rollIndex + 1) % rollBuffer.size
                    }
                } else {
                    isRollCapturing = true
                }

                // Bass Boost Effect
                if (bassBoostEnabled) {
                    val bass = processSample(processed, lowCoeffs, lowState[c])
                    processed += bass * 0.65
                }

                // EQ Filters: Low, Mid, High
                processed = processSample(processed, lowCoeffs, lowState[c])
                processed = processSample(processed, midCoeffs, midState[c])
                processed = processSample(processed, highCoeffs, highState[c])

                // DJ Filter: Low-Pass or High-Pass
                processed = processSample(processed, filterCoeffs, filterState[c])

                // Echo Effect
                val eDelayIdx = (echoIndex - echoDelaySamples + echoBuffer.size) % echoBuffer.size
                if (echoEnabled) {
                    val delayedSample = echoBuffer[eDelayIdx]
                    processed += delayedSample * 0.45
                }
                echoBuffer[echoIndex] = processed
                echoIndex = (echoIndex + 1) % echoBuffer.size

                // Reverb Effect
                val rDelayIdx = (reverbIndex - reverbDelaySamples + reverbBuffer.size) % reverbBuffer.size
                if (reverbEnabled) {
                    val delayedSample = reverbBuffer[rDelayIdx]
                    processed += delayedSample * 0.4
                }
                reverbBuffer[reverbIndex] = inputSample + reverbBuffer[rDelayIdx] * 0.35
                reverbIndex = (reverbIndex + 1) % reverbBuffer.size

                // Flanger Effect
                if (flangerEnabled) {
                    val fDelayIdx = (flangerIndex - flangerModDelay + flangerBuffer.size) % flangerBuffer.size
                    val flangedSample = flangerBuffer[fDelayIdx]
                    processed = (processed * 0.6) + (flangedSample * 0.5)
                }
                flangerBuffer[flangerIndex] = processed
                flangerIndex = (flangerIndex + 1) % flangerBuffer.size

                // Bit Crusher Effect
                if (bitCrusherEnabled) {
                    sampleDecimator++
                    if (sampleDecimator % 3 == 0) {
                        val quantizeStep = 1024
                        crushHoldSample = ((processed / quantizeStep).toInt() * quantizeStep).toDouble()
                    }
                    processed = crushHoldSample
                }

                // Apply Turntable Brake
                processed *= brakeMultiplier

                // Master Gain
                processed *= masterGain

                // Energy for BPM detection (mono sum)
                if (c == 0) instantEnergy += processed * processed

                val outShortValue = processed.toInt().coerceIn(-32768, 32767)
                shortOut.put(outShortValue.toShort())

                val absProcessed = abs(processed)
                if (absProcessed > maxVal) {
                    maxVal = absProcessed
                }
            }
        }

        // Live rolling waveform peak tracking
        val peak = (maxVal / 32768.0).toFloat().coerceIn(0.0f, 1.0f)
        System.arraycopy(localWaveform, 1, localWaveform, 0, localWaveform.size - 1)
        localWaveform[localWaveform.size - 1] = peak
        _waveformHistory.value = localWaveform.clone()

        // Analyze BPM
        val divisor = (sampleCount.coerceAtLeast(channels) / channels).coerceAtLeast(1)
        analyzeBpm(instantEnergy / divisor, totalFramesProcessed, sampleRate)

        inputBuffer.position(inputBuffer.limit())
        buffer.limit(inputSize)
        buffer.position(0)
        outputBuffer = buffer

        onBufferProcessed?.invoke(outputBuffer.duplicate(), inputAudioFormat)
    }

    override fun getOutput(): ByteBuffer {
        val b = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return b
    }

    private fun analyzeBpm(energy: Double, frames: Long, sampleRate: Int) {
        if (!isAutoBpmEnabled || sampleRate <= 0) return
        val avgEnergy = if (energyHistory.isNotEmpty()) energyHistory.average() else 0.0
        val sensitivity = 1.3

        if (energy > sensitivity * avgEnergy && avgEnergy > 0) {
            val now = (frames * 1000) / sampleRate
            val interval = now - lastBeatTimestamp
            if (interval in 270..1500) {
                intervals.add(interval)
                if (intervals.size > 10) intervals.removeAt(0)
                val avgInterval = intervals.average()
                if (avgInterval > 0) _bpm.value = (60000.0 / avgInterval).toInt()
                lastBeatTimestamp = now
            } else if (interval > 1500) {
                lastBeatTimestamp = now
            }
        }

        energyHistory.add(energy)
        if (energyHistory.size > HISTORY_SIZE) energyHistory.removeAt(0)
    }

    override fun queueEndOfStream() {}
    override fun isEnded(): Boolean = false
    override fun flush() {
        isAutoBpmEnabled = true
        energyHistory.clear()
        intervals.clear()
        _bpm.value = 0
        lastBeatTimestamp = 0L
        totalFramesProcessed = 0L
        localWaveform.fill(0.0f)
        _waveformHistory.value = localWaveform.clone()
        echoBuffer.fill(0.0)
        reverbBuffer.fill(0.0)
        flangerBuffer.fill(0.0)
        rollBuffer.fill(0.0)
        rollIndex = 0
        isRollCapturing = true
        brakeMultiplier = 1.0
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
    }
}
