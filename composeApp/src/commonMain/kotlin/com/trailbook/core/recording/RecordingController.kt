package com.trailbook.core.recording

import com.trailbook.core.geo.GeoMath
import com.trailbook.core.model.ActivityType
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.prefs.RecordingSettings
import com.trailbook.core.stats.StatisticsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

enum class RecordingState { IDLE, RECORDING, PAUSED }

@Serializable
data class RecordingSession(
    val startedAt: Long,
    val activityTypeId: String = ActivityType.HIKING.id,
    val state: RecordingState = RecordingState.RECORDING,
    val points: List<TrackPoint> = emptyList(),
    val name: String? = null,
    val pausedAccumulatedMs: Long = 0,
    val lastPauseAt: Long? = null,
) {
    val isRunning: Boolean get() = state == RecordingState.RECORDING
}

data class RecordingUpdate(
    val session: RecordingSession,
    val liveDistanceMeters: Double,
    val liveDurationSeconds: Double,
    val liveSpeedMps: Double,
    val currentPoint: TrackPoint?,
)

interface LocationSource {
    suspend fun current(): TrackPoint?
    fun updates(): kotlinx.coroutines.flow.Flow<TrackPoint>
}

class RecordingController(
    private val locationSource: LocationSource,
    private val clock: () -> Long,
    private val idGenerator: () -> String,
) {
    private val _session = MutableStateFlow<RecordingSession?>(null)
    val session: StateFlow<RecordingSession?> = _session.asStateFlow()

    private val _update = MutableStateFlow<RecordingUpdate?>(null)
    val update: StateFlow<RecordingUpdate?> = _update.asStateFlow()

    fun restore(saved: RecordingSession?) {
        _session.value = saved
        recompute()
    }

    fun start(activityType: ActivityType, settings: RecordingSettings): Result<RecordingSession> {
        if (_session.value != null) {
            return Result.failure(IllegalStateException("A recording is already active"))
        }
        val now = clock()
        val session = RecordingSession(
            startedAt = now,
            activityTypeId = activityType.id,
            state = RecordingState.RECORDING,
        )
        _session.value = session
        recompute(settings)
        return Result.success(session)
    }

    fun pause(): RecordingSession? {
        val s = _session.value ?: return null
        if (s.state != RecordingState.RECORDING) return s
        val updated = s.copy(state = RecordingState.PAUSED, lastPauseAt = clock())
        _session.value = updated
        recompute()
        return updated
    }

    fun resume(): RecordingSession? {
        val s = _session.value ?: return null
        if (s.state != RecordingState.PAUSED) return s
        val now = clock()
        val pausedMs = s.pausedAccumulatedMs + (s.lastPauseAt?.let { now - it } ?: 0)
        val updated = s.copy(state = RecordingState.RECORDING, pausedAccumulatedMs = pausedMs, lastPauseAt = null)
        _session.value = updated
        recompute()
        return updated
    }

    fun onLocationUpdate(point: TrackPoint, settings: RecordingSettings): Boolean {
        val s = _session.value ?: return false
        if (s.state != RecordingState.RECORDING) return false
        if (!point.latitude.isFinite() || !point.longitude.isFinite()) return false
        if (point.accuracyM != null && point.accuracyM > settings.minAccuracyMeters) return false
        val last = s.points.lastOrNull()
        if (last != null) {
            if (point.time - last.time < settings.minIntervalSeconds * 1000L) return false
            val dist = GeoMath.haversineMeters(last.toGeoPoint(), com.trailbook.core.geo.GeoPoint(point.latitude, point.longitude))
            if (dist < settings.minDistanceMeters && point.elevation == last.elevation) return false
        }
        val updated = s.copy(points = s.points + point)
        _session.value = updated
        recompute(settings)
        return true
    }

    fun rename(name: String) {
        val s = _session.value ?: return
        _session.value = s.copy(name = name)
    }

    fun discard() {
        _session.value = null
        _update.value = null
    }

    fun finish(): RecordingSession? {
        val s = _session.value ?: return null
        val now = clock()
        val pausedMs = s.pausedAccumulatedMs + (s.lastPauseAt?.let { now - it } ?: 0)
        _session.value = s.copy(state = RecordingState.IDLE, pausedAccumulatedMs = pausedMs)
        return _session.value
    }

    private fun recompute(settings: RecordingSettings = RecordingSettings()) {
        val s = _session.value ?: return
        val stats = StatisticsEngine.compute(s.points, com.trailbook.core.stats.StatisticsOptions(
            maxAccuracyMeters = settings.minAccuracyMeters
        ))
        val now = clock()
        val elapsed = when (s.state) {
            RecordingState.RECORDING -> now - s.startedAt - s.pausedAccumulatedMs
            RecordingState.PAUSED -> (s.lastPauseAt ?: now) - s.startedAt - s.pausedAccumulatedMs
            RecordingState.IDLE -> if (s.points.isNotEmpty()) s.points.last().time - s.startedAt else 0
        }.coerceAtLeast(0) / 1000.0
        val distance = stats?.distanceMeters ?: 0.0
        val speed = if (elapsed > 0) distance / elapsed else 0.0
        _update.value = RecordingUpdate(s, distance, elapsed, speed, s.points.lastOrNull())
    }
}
