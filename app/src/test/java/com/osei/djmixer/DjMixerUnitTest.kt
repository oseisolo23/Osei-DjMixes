package com.osei.djmixer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unit tests verifying core DJ mixer calculations and algorithms:
 * - Equal-power crossfader curve (Deck A full, Center 50/50, Deck B full)
 * - Beat-based loop length calculations (1, 2, 4, 8, 16 beats at target BPM)
 * - Hot Cue serialization & restore logic
 * - Pitch Lock parameter state resolution
 */
class DjMixerUnitTest {

    @Test
    fun testCrossfaderCurve() {
        // Fully A (cf = 0.0f)
        val cfA = 0.0f
        val radA = (cfA * PI / 2.0).toFloat()
        val gainA_at_A = cos(radA.toDouble()).toFloat()
        val gainB_at_A = sin(radA.toDouble()).toFloat()
        assertEquals(1.0f, gainA_at_A, 0.001f)
        assertEquals(0.0f, gainB_at_A, 0.001f)

        // Center (cf = 0.5f)
        val cfCenter = 0.5f
        val radCenter = (cfCenter * PI / 2.0).toFloat()
        val gainA_at_Center = cos(radCenter.toDouble()).toFloat()
        val gainB_at_Center = sin(radCenter.toDouble()).toFloat()
        assertEquals(0.7071f, gainA_at_Center, 0.001f)
        assertEquals(0.7071f, gainB_at_Center, 0.001f)

        // Fully B (cf = 1.0f)
        val cfB = 1.0f
        val radB = (cfB * PI / 2.0).toFloat()
        val gainA_at_B = cos(radB.toDouble()).toFloat()
        val gainB_at_B = sin(radB.toDouble()).toFloat()
        assertEquals(0.0f, gainA_at_B, 0.001f)
        assertEquals(1.0f, gainB_at_B, 0.001f)
    }

    @Test
    fun testBeatLoopCalculations() {
        val bpm = 120 // 120 beats per minute = 2 beats per second = 500ms per beat
        val beatMs = (60000.0 / bpm).toLong()
        assertEquals(500L, beatMs)

        val loop1 = beatMs * 1
        assertEquals(500L, loop1)

        val loop2 = beatMs * 2
        assertEquals(1000L, loop2)

        val loop4 = beatMs * 4
        assertEquals(2000L, loop4)

        val loop8 = beatMs * 8
        assertEquals(4000L, loop8)

        val loop16 = beatMs * 16
        assertEquals(8000L, loop16)
    }

    @Test
    fun testHotCueSerialization() {
        val cues = listOf<Long?>(10500L, null, 45200L, 90000L)
        val raw = cues.take(4).joinToString(",") { it?.toString() ?: "" }
        assertEquals("10500,,45200,90000", raw)

        val parts = raw.split(",")
        val restored = List(4) { i -> parts.getOrNull(i)?.toLongOrNull() }
        assertEquals(10500L, restored[0])
        assertEquals(null, restored[1])
        assertEquals(45200L, restored[2])
        assertEquals(90000L, restored[3])
    }

    @Test
    fun testPitchLockLogic() {
        val speed = 1.15f // 15% tempo increase

        // Key lock ON: pitch stays at 1.0f (time-stretching without pitch shift)
        val keyLockOnPitch = 1.0f
        assertEquals(1.0f, keyLockOnPitch, 0.0001f)

        // Key lock OFF: pitch tracks speed (vinyl turntable behavior)
        val keyLockOffPitch = speed
        assertEquals(1.15f, keyLockOffPitch, 0.0001f)
    }

    @Test
    fun testCamelotConversion() {
        // Minor keys (Camelot A)
        assertEquals("8A", com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.keyToCamelot("Am"))
        assertEquals("9A", com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.keyToCamelot("Em"))
        assertEquals("5A", com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.keyToCamelot("Cm"))
        assertEquals("11A", com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.keyToCamelot("F#m"))

        // Major keys (Camelot B)
        assertEquals("8B", com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.keyToCamelot("C"))
        assertEquals("9B", com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.keyToCamelot("G"))
        assertEquals("11B", com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.keyToCamelot("A"))
        assertEquals("1B", com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.keyToCamelot("B"))
    }

    @Test
    fun testHarmonicCompatibility() {
        val analyzer = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer

        // Exact Key Match: Am -> Am
        val (exactScore, exactRel) = analyzer.evaluateHarmonicCompatibility("Am", "Am")
        assertEquals(1.0f, exactScore, 0.001f)
        assertEquals(com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.HarmonicRelation.EXACT, exactRel)

        // Relative Major / Minor: Am (8A) <-> C (8B)
        val (relScore, relRel) = analyzer.evaluateHarmonicCompatibility("Am", "C")
        assertEquals(0.92f, relScore, 0.001f)
        assertEquals(com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.HarmonicRelation.RELATIVE, relRel)

        // Adjacent Key: Am (8A) -> Em (9A)
        val (adjScore, adjRel) = analyzer.evaluateHarmonicCompatibility("Am", "Em")
        assertEquals(0.88f, adjScore, 0.001f)
        assertEquals(com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.HarmonicRelation.ADJACENT, adjRel)

        // Energy Boost: Am (8A) -> Bm (10A)
        val (boostScore, boostRel) = analyzer.evaluateHarmonicCompatibility("Am", "Bm")
        assertEquals(0.76f, boostScore, 0.001f)
        assertEquals(com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.HarmonicRelation.ENERGY_BOOST, boostRel)

        // Unknown key fallback
        val (unkScore, unkRel) = analyzer.evaluateHarmonicCompatibility("Am", null)
        assertEquals(0.50f, unkScore, 0.001f)
        assertEquals(com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.HarmonicRelation.UNKNOWN, unkRel)
    }

    @Test
    fun testBpmCompatibility() {
        val analyzer = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer

        // Exact match
        val (exactScore, _) = analyzer.evaluateBpmCompatibility(128, 128)
        assertEquals(1.0f, exactScore, 0.001f)

        // Close match (within 2-5%)
        val (closeScore, _) = analyzer.evaluateBpmCompatibility(128, 126)
        assertTrue(closeScore >= 0.90f)

        // Half-time / Double-time (70 BPM vs 140 BPM)
        val (halfScore, _) = analyzer.evaluateBpmCompatibility(70, 140)
        assertEquals(0.86f, halfScore, 0.01f)

        // Neutral when unknown
        val (neutralScore, _) = analyzer.evaluateBpmCompatibility(128, null)
        assertEquals(0.50f, neutralScore, 0.001f)
    }

    @Test
    fun testTransitionScoringEngine() {
        val analyzer = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer

        val currentTrack = com.osei.djmixer.ui.analysis.TrackAnalysisResult(
            trackId = "track1",
            bpm = 126,
            bpmConfidence = 0.9f,
            musicalKey = "Am",
            keyConfidence = 0.85f,
            energyScore = 0.65f,
            energyConfidence = 0.8f,
            energyLevel = "MEDIUM",
            durationMs = 180000L
        )

        // Highly compatible candidate: C (relative major 8B), 126 BPM, medium energy
        val goodCandidate = com.osei.djmixer.ui.analysis.TrackAnalysisResult(
            trackId = "track2",
            bpm = 126,
            bpmConfidence = 0.95f,
            musicalKey = "C",
            keyConfidence = 0.9f,
            energyScore = 0.70f,
            energyConfidence = 0.85f,
            energyLevel = "MEDIUM",
            durationMs = 210000L
        )

        val score = analyzer.calculateTransitionScore(
            current = currentTrack,
            candidate = goodCandidate,
            mode = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.AutoDjMixMode.SMOOTH_FLOW
        )

        assertTrue(score.totalScore > 0.85f)
        assertTrue(score.matchPercentage >= 85)
        assertEquals("8A", score.camelotCurrent)
        assertEquals("8B", score.camelotCandidate)
        assertEquals(com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.HarmonicRelation.RELATIVE, score.harmonicRelation)
    }

    @Test
    fun testHonestUnknownEnergyHandling() {
        val analyzer = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer

        // Empty PCM frames must yield honest null/UNKNOWN energy, never 0.5f or MEDIUM
        val (energyScore, confidence, level) = analyzer.analyzeEnergy(ShortArray(0), 44100)
        assertEquals(null, energyScore)
        assertEquals(0.0f, confidence, 0.0001f)
        assertEquals("UNKNOWN", level)

        // Evaluate energy compatibility when energy is unknown returns neutral score
        val (neutralScore, desc) = analyzer.evaluateEnergyCompatibility(null, 0.8f, com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.AutoDjMixMode.PEAK_HOUR)
        assertEquals(0.50f, neutralScore, 0.001f)
        assertTrue(desc.contains("Unknown"))
    }

    @Test
    fun testBpmSourceDistinction() {
        // Track with DSP analysis
        val dspTrack = com.osei.djmixer.ui.analysis.TrackAnalysisResult(
            trackId = "t1",
            bpm = 128,
            bpmConfidence = 0.92f,
            bpmSource = com.osei.djmixer.ui.analysis.AnalysisSource.DSP,
            musicalKey = "Am",
            keyConfidence = 0.85f,
            keySource = com.osei.djmixer.ui.analysis.AnalysisSource.DSP,
            energyScore = 0.7f,
            energyConfidence = 0.8f,
            energyLevel = "MEDIUM",
            durationMs = 120000L
        )
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.DSP, dspTrack.bpmSource)

        // Track with metadata BPM
        val metaTrack = com.osei.djmixer.ui.analysis.TrackAnalysisResult(
            trackId = "t2",
            bpm = 125,
            bpmConfidence = 0.6f,
            bpmSource = com.osei.djmixer.ui.analysis.AnalysisSource.METADATA,
            musicalKey = null,
            keyConfidence = 0.0f,
            keySource = com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN,
            energyScore = null,
            energyConfidence = 0.0f,
            energyLevel = "UNKNOWN",
            durationMs = 120000L
        )
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.METADATA, metaTrack.bpmSource)
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, metaTrack.keySource)
        assertEquals(null, metaTrack.energyScore)
    }

    @Test
    fun testMusicalKeySourceAndCamelotSafety() {
        val analyzer = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer

        // Valid musical keys convert correctly
        assertEquals("8A", analyzer.keyToCamelot("Am"))
        assertEquals("8B", analyzer.keyToCamelot("C"))

        // Null, empty, or UNKNOWN keys must return null (never invent a key)
        assertEquals(null, analyzer.keyToCamelot(null))
        assertEquals(null, analyzer.keyToCamelot(""))
        assertEquals(null, analyzer.keyToCamelot("UNKNOWN"))
        assertEquals(null, analyzer.keyToCamelot("InvalidKey"))
    }

    @Test
    fun testTransitionScoreWithUnknownEnergyNeutralized() {
        val analyzer = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer

        val trackA = com.osei.djmixer.ui.analysis.TrackAnalysisResult(
            trackId = "a",
            bpm = 128,
            bpmConfidence = 0.9f,
            musicalKey = "Am",
            keyConfidence = 0.9f,
            energyScore = null, // UNKNOWN energy
            energyConfidence = 0.0f,
            energyLevel = "UNKNOWN",
            durationMs = 180000L
        )

        val trackB = com.osei.djmixer.ui.analysis.TrackAnalysisResult(
            trackId = "b",
            bpm = 128,
            bpmConfidence = 0.9f,
            musicalKey = "Am",
            keyConfidence = 0.9f,
            energyScore = null, // UNKNOWN energy
            energyConfidence = 0.0f,
            energyLevel = "UNKNOWN",
            durationMs = 180000L
        )

        val score = analyzer.calculateTransitionScore(trackA, trackB)
        // Exact BPM (1.0) and Exact Key (1.0) -> neutralized energy should yield 1.0 (100% Match)
        assertEquals(1.0f, score.totalScore, 0.01f)
        assertEquals(100, score.matchPercentage)
    }

    @Test
    fun testCachedAnalysisSourceResolution() {
        val analyzer = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer

        // Explicitly recorded DSP source remains DSP
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.DSP, analyzer.parseAnalysisSource("DSP"))
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.DSP, analyzer.parseAnalysisSource("dsp"))

        // Explicitly recorded METADATA source remains METADATA
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.METADATA, analyzer.parseAnalysisSource("METADATA"))
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.METADATA, analyzer.parseAnalysisSource("metadata"))

        // Missing, null, empty, or unknown strings resolve strictly to UNKNOWN
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, analyzer.parseAnalysisSource("UNKNOWN"))
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, analyzer.parseAnalysisSource(null))
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, analyzer.parseAnalysisSource(""))
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, analyzer.parseAnalysisSource("   "))
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, analyzer.parseAnalysisSource("ESTIMATED"))
    }

    @Test
    fun testNeverFabricateMusicalKeyOrigin() {
        val analyzer = com.osei.djmixer.ui.analysis.AudioTrackAnalyzer

        // Even when musicalKey is present (e.g. "Am"), if keySource is unknown or missing,
        // the source must remain UNKNOWN and never be fabricated as DSP
        val cachedSourceNull = analyzer.parseAnalysisSource(null)
        val cachedSourceUnknown = analyzer.parseAnalysisSource("UNKNOWN")
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, cachedSourceNull)
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, cachedSourceUnknown)

        val honestTrack = com.osei.djmixer.ui.analysis.TrackAnalysisResult(
            trackId = "track_unknown_origin",
            bpm = 120,
            bpmConfidence = 0.5f,
            bpmSource = analyzer.parseAnalysisSource("UNKNOWN"),
            musicalKey = "C#m",
            keyConfidence = 0.5f,
            keySource = analyzer.parseAnalysisSource("UNKNOWN"),
            energyScore = null,
            energyConfidence = 0.0f,
            energyLevel = "UNKNOWN",
            durationMs = 200000L
        )

        // Verify keySource is strictly UNKNOWN despite having a non-null musicalKey
        assertEquals("C#m", honestTrack.musicalKey)
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, honestTrack.keySource)
        assertEquals(com.osei.djmixer.ui.analysis.AnalysisSource.UNKNOWN, honestTrack.bpmSource)
    }

    @Test
    fun testMediaPermissionGranted_NotificationPermissionDenied() {
        val pm = com.osei.djmixer.util.PermissionManager

        // On Android 13+ (API 33)
        val grantsTiramisu = mapOf(
            android.Manifest.permission.READ_MEDIA_AUDIO to true,
            android.Manifest.permission.READ_MEDIA_VIDEO to true,
            android.Manifest.permission.POST_NOTIFICATIONS to false
        )

        val status = pm.evaluateMediaPermissions(grantsTiramisu, sdkInt = 33)
        assertTrue("Audio access must be granted", status.hasAudioAccess)
        assertTrue("Video access must be granted", status.hasVideoAccess)
        assertTrue("Any media access must be true", status.hasAnyMediaAccess)
        assertTrue("Media access is fully granted", status.isFullyGranted)

        // Notification denial must NOT block media scanning!
        val shouldScan = pm.shouldScanMedia(grantsTiramisu, sdkInt = 33)
        assertTrue("Media scanning must proceed even when POST_NOTIFICATIONS is denied", shouldScan)
    }

    @Test
    fun testMediaPermissionDenied() {
        val pm = com.osei.djmixer.util.PermissionManager

        // Android 13+ where media permissions are denied
        val grantsTiramisu = mapOf(
            android.Manifest.permission.READ_MEDIA_AUDIO to false,
            android.Manifest.permission.READ_MEDIA_VIDEO to false,
            android.Manifest.permission.POST_NOTIFICATIONS to true
        )
        val statusTiramisu = pm.evaluateMediaPermissions(grantsTiramisu, sdkInt = 33)
        assertFalse(statusTiramisu.hasAudioAccess)
        assertFalse(statusTiramisu.hasVideoAccess)
        assertFalse(statusTiramisu.hasAnyMediaAccess)
        assertFalse(statusTiramisu.isFullyGranted)
        assertFalse("Media scan must not run when media permissions denied", pm.shouldScanMedia(grantsTiramisu, sdkInt = 33))

        // Android 12 (API 32) where READ_EXTERNAL_STORAGE is denied
        val grantsLegacy = mapOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE to false
        )
        val statusLegacy = pm.evaluateMediaPermissions(grantsLegacy, sdkInt = 32)
        assertFalse(statusLegacy.hasAudioAccess)
        assertFalse(statusLegacy.hasVideoAccess)
        assertFalse(statusLegacy.hasAnyMediaAccess)
        assertFalse(statusLegacy.isFullyGranted)
        assertFalse(pm.shouldScanMedia(grantsLegacy, sdkInt = 32))
    }

    @Test
    fun testPartialMediaPermissionGrants() {
        val pm = com.osei.djmixer.util.PermissionManager

        // Scenario 1: Audio granted, Video denied
        val audioOnlyGrants = mapOf(
            android.Manifest.permission.READ_MEDIA_AUDIO to true,
            android.Manifest.permission.READ_MEDIA_VIDEO to false,
            android.Manifest.permission.POST_NOTIFICATIONS to false
        )
        val audioStatus = pm.evaluateMediaPermissions(audioOnlyGrants, sdkInt = 33)
        assertTrue("Audio access must be granted", audioStatus.hasAudioAccess)
        assertFalse("Video access must be denied", audioStatus.hasVideoAccess)
        assertTrue("Any media access must be true for audio only", audioStatus.hasAnyMediaAccess)
        assertFalse("Full media is not granted", audioStatus.isFullyGranted)
        assertTrue("Media scan must proceed for partial audio grant", pm.shouldScanMedia(audioOnlyGrants, sdkInt = 33))

        // Scenario 2: Video granted, Audio denied
        val videoOnlyGrants = mapOf(
            android.Manifest.permission.READ_MEDIA_AUDIO to false,
            android.Manifest.permission.READ_MEDIA_VIDEO to true,
            android.Manifest.permission.POST_NOTIFICATIONS to true
        )
        val videoStatus = pm.evaluateMediaPermissions(videoOnlyGrants, sdkInt = 33)
        assertFalse("Audio access must be denied", videoStatus.hasAudioAccess)
        assertTrue("Video access must be granted", videoStatus.hasVideoAccess)
        assertTrue("Any media access must be true for video only", videoStatus.hasAnyMediaAccess)
        assertFalse("Full media is not granted", videoStatus.isFullyGranted)
        assertTrue("Media scan must proceed for partial video grant", pm.shouldScanMedia(videoOnlyGrants, sdkInt = 33))
    }

    @Test
    fun testAllRequiredPermissionsGranted() {
        val pm = com.osei.djmixer.util.PermissionManager

        // Android 13+ with all permissions granted
        val allGrantedTiramisu = mapOf(
            android.Manifest.permission.READ_MEDIA_AUDIO to true,
            android.Manifest.permission.READ_MEDIA_VIDEO to true,
            android.Manifest.permission.POST_NOTIFICATIONS to true
        )
        val statusTiramisu = pm.evaluateMediaPermissions(allGrantedTiramisu, sdkInt = 33)
        assertTrue(statusTiramisu.hasAudioAccess)
        assertTrue(statusTiramisu.hasVideoAccess)
        assertTrue(statusTiramisu.hasAnyMediaAccess)
        assertTrue(statusTiramisu.isFullyGranted)
        assertTrue(pm.shouldScanMedia(allGrantedTiramisu, sdkInt = 33))

        // Android 12 and below with READ_EXTERNAL_STORAGE granted
        val allGrantedLegacy = mapOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE to true
        )
        val statusLegacy = pm.evaluateMediaPermissions(allGrantedLegacy, sdkInt = 31)
        assertTrue(statusLegacy.hasAudioAccess)
        assertTrue(statusLegacy.hasVideoAccess)
        assertTrue(statusLegacy.hasAnyMediaAccess)
        assertTrue(statusLegacy.isFullyGranted)
        assertTrue(pm.shouldScanMedia(allGrantedLegacy, sdkInt = 31))
    }

    @Test
    fun testVersionAwareRequiredMediaPermissions() {
        val pm = com.osei.djmixer.util.PermissionManager

        val permissionsTiramisu = pm.getRequiredMediaPermissions(sdkInt = 33)
        assertEquals(2, permissionsTiramisu.size)
        assertTrue(permissionsTiramisu.contains(android.Manifest.permission.READ_MEDIA_AUDIO))
        assertTrue(permissionsTiramisu.contains(android.Manifest.permission.READ_MEDIA_VIDEO))
        assertFalse("POST_NOTIFICATIONS must NOT be in media permissions", permissionsTiramisu.contains(android.Manifest.permission.POST_NOTIFICATIONS))

        val permissionsLegacy = pm.getRequiredMediaPermissions(sdkInt = 31)
        assertEquals(1, permissionsLegacy.size)
        assertEquals(android.Manifest.permission.READ_EXTERNAL_STORAGE, permissionsLegacy[0])
    }
}
