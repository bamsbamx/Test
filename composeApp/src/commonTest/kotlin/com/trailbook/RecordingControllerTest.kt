package com.trailbook

import com.trailbook.core.model.ActivityType
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.recording.RecordingController
import com.trailbook.core.recording.RecordingState
import com.trailbook.core.recording.LocationSource
import com.trailbook.core.prefs.RecordingSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingControllerTest {

    private class FakeLocationSource : LocationSource {
        override suspend fun current(): TrackPoint? = null
        override fun updates() = flowOf<TrackPoint>()
    }

    private fun controller(clock: () -> Long) = RecordingController(FakeLocationSource(), clock) { "id-1" }

    private val settings = RecordingSettings(minAccuracyMeters = 25.0, minDistanceMeters = 1.0, minIntervalSeconds = 0)

    private fun point(lat: Double, lon: Double, time: Long, accuracy: Double? = 5.0) =
        TrackPoint(lat, lon, 100.0, time, accuracyM = accuracy)

    @Test
    fun startCreatesRecordingSession() {
        var time = 1000L
        val c = controller { time }
        val result = c.start(ActivityType.HIKING, settings)
        assertTrue(result.isSuccess)
        assertEquals(RecordingState.RECORDING, c.session.value?.state)
        assertEquals(1000L, c.session.value?.startedAt)
        assertEquals(ActivityType.HIKING.id, c.session.value?.activityTypeId)
    }

    @Test
    fun startFailsWhenAlreadyRecording() {
        val c = controller { 1000L }
        c.start(ActivityType.HIKING, settings)
        assertTrue(c.start(ActivityType.CYCLING, settings).isFailure)
    }

    @Test
    fun pauseResumeAccumulatesPausedTime() {
        var time = 1000L
        val c = controller { time }
        c.start(ActivityType.HIKING, settings)
        time = 60_000L
        c.pause()
        assertEquals(RecordingState.PAUSED, c.session.value?.state)
        time = 120_000L
        c.resume()
        assertEquals(60_000L, c.session.value?.pausedAccumulatedMs)
        assertEquals(RecordingState.RECORDING, c.session.value?.state)
    }

    @Test
    fun locationUpdatesAreFilteredByAccuracy() {
        val c = controller { 1000L }
        c.start(ActivityType.HIKING, settings)
        assertFalse(c.onLocationUpdate(point(47.0, 8.0, 2000L, accuracy = 100.0), settings))
        assertTrue(c.onLocationUpdate(point(47.0, 8.0, 2000L), settings))
        assertEquals(1, c.session.value?.points?.size)
    }

    @Test
    fun locationUpdatesIgnoredWhilePaused() {
        val c = controller { 1000L }
        c.start(ActivityType.HIKING, settings)
        c.pause()
        assertFalse(c.onLocationUpdate(point(47.0, 8.0, 2000L), settings))
        assertEquals(0, c.session.value?.points?.size)
    }

    @Test
    fun invalidCoordinatesRejected() {
        val c = controller { 1000L }
        c.start(ActivityType.HIKING, settings)
        assertFalse(c.onLocationUpdate(TrackPoint(Double.NaN, 8.0, null, 2000L), settings))
    }

    @Test
    fun finishProducesIdleSessionWithPoints() {
        val c = controller { 1000L }
        c.start(ActivityType.HIKING, settings)
        c.onLocationUpdate(point(47.0, 8.0, 2000L), settings)
        c.onLocationUpdate(point(47.001, 8.0, 3000L), settings)
        val finished = c.finish()
        assertNotNull(finished)
        assertEquals(RecordingState.IDLE, finished.state)
        assertEquals(2, finished.points.size)
        assertEquals(RecordingState.IDLE, c.session.value?.state)
        c.discard()
        assertNull(c.session.value)
    }

    @Test
    fun restorePreservesSession() {
        val c = controller { 1000L }
        c.start(ActivityType.CYCLING, settings)
        c.onLocationUpdate(point(47.0, 8.0, 2000L), settings)
        val saved = c.session.value
        val restored = RecordingController(FakeLocationSource(), { 1000L }) { "id-1" }
        restored.restore(saved)
        assertEquals(saved, restored.session.value)
        assertEquals(RecordingState.RECORDING, restored.session.value?.state)
    }

    @Test
    fun liveDistanceAccumulates() = runTest {
        var time = 1000L
        val c = controller { time }
        c.start(ActivityType.HIKING, settings)
        time = 30_000L
        c.onLocationUpdate(point(47.0, 8.0, 2000L), settings)
        c.onLocationUpdate(point(47.01, 8.0, 3000L), settings)
        val update = c.update.value
        assertNotNull(update)
        assertTrue(update.liveDistanceMeters > 1000.0)
        assertTrue(update.liveSpeedMps > 0.0)
    }

    @Test
    fun discardClearsSession() {
        val c = controller { 1000L }
        c.start(ActivityType.HIKING, settings)
        c.discard()
        assertNull(c.session.value)
    }
}
