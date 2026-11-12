package com.trailbook.app

import com.trailbook.core.elevation.ElevationService
import com.trailbook.core.format.FormatRegistry
import com.trailbook.core.net.HttpTransport
import com.trailbook.core.prefs.AppSettings
import com.trailbook.core.prefs.FilePreferencesRepository
import com.trailbook.core.prefs.PreferencesRepository
import com.trailbook.core.recording.LocationSource
import com.trailbook.core.recording.LocationException
import com.trailbook.core.recording.RecordingController
import com.trailbook.core.recording.RecordingSession
import com.trailbook.core.routing.RoutingService
import com.trailbook.core.search.GeocodingService
import com.trailbook.core.search.RecentSearchStore
import com.trailbook.core.sync.AccountService
import com.trailbook.core.sync.BillingAdapter
import com.trailbook.core.sync.SyncEngine
import com.trailbook.core.sync.SuuntoService
import com.trailbook.core.sync.TestModeBillingAdapter
import com.trailbook.core.weather.WeatherService
import com.trailbook.data.FileMarkerListRepository
import com.trailbook.data.FileMarkerRepository
import com.trailbook.data.FileRouteRepository
import com.trailbook.data.FileTrackRepository
import com.trailbook.data.JsonFileStore
import com.trailbook.data.MarkerListRepository
import com.trailbook.data.MarkerRepository
import com.trailbook.data.RouteRepository
import com.trailbook.data.TrackRepository
import com.trailbook.platform.AppFileSystem
import com.trailbook.platform.FeedbackPlayer
import com.trailbook.platform.FilePicker
import com.trailbook.platform.FileSharer
import com.trailbook.platform.LocationUpdate
import com.trailbook.platform.LocationProvider
import com.trailbook.platform.NetworkMonitor
import com.trailbook.platform.PermissionManager
import com.trailbook.platform.RecordingNotification
import com.trailbook.platform.SecureStore
import com.trailbook.platform.SensorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(
    val fileSystem: AppFileSystem,
    val httpTransport: HttpTransport,
    val networkMonitor: NetworkMonitor,
    val secureStore: SecureStore,
    val permissionManager: PermissionManager,
    val locationProvider: LocationProvider,
    val sensorProvider: SensorProvider,
    val fileSharer: FileSharer,
    val filePicker: FilePicker = FilePicker(),
    val recordingNotification: RecordingNotification,
    val feedbackPlayer: FeedbackPlayer,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dataDir: String = fileSystem.dataDirectory()
    private val store = JsonFileStore(fileSystem, dataDir)
    private val recordingStore = JsonFileStore(fileSystem, dataDir)

    val trackRepository: TrackRepository = FileTrackRepository(store)
    val routeRepository: RouteRepository = FileRouteRepository(store)
    val markerRepository: MarkerRepository = FileMarkerRepository(store)
    val markerListRepository: MarkerListRepository = FileMarkerListRepository(store)
    val preferences: PreferencesRepository = FilePreferencesRepository(store)

    val weatherService = WeatherService(httpTransport) { SystemTime.now() }
    val geocodingService = GeocodingService(httpTransport)
    val elevationService = ElevationService(httpTransport)
    val routingService = RoutingService(httpTransport, elevationService)
    val formatRegistry = FormatRegistry({ Ids.next() }, { SystemTime.now() })
    val accountService = AccountService(httpTransport, secureStore) { SystemTime.now() }
    val syncEngine = SyncEngine(httpTransport, accountService, trackRepository, markerRepository) { SystemTime.now() }
    val suuntoService = SuuntoService(httpTransport, secureStore) { SystemTime.now() }
    val billing: BillingAdapter = TestModeBillingAdapter(preferences)
    val recentSearches = RecentSearchStore()

    val recording = RecordingController(
        locationSource = object : LocationSource {
            override suspend fun current(): com.trailbook.core.model.TrackPoint? = locationProvider.lastKnown()
            override fun updates(): kotlinx.coroutines.flow.Flow<com.trailbook.core.model.TrackPoint> =
                locationProvider.updates(2000).map { update ->
                    when (update) {
                        is LocationUpdate.Success -> update.point
                        is LocationUpdate.Denied -> throw LocationException(update.message)
                        is LocationUpdate.Unavailable -> throw LocationException(update.message)
                        is LocationUpdate.Failed -> throw LocationException(update.message)
                    }
                }
        },
        clock = { SystemTime.now() },
        idGenerator = { Ids.next() },
    )

    private val _settings = MutableStateFlow(preferences.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _recordingLocationError = MutableStateFlow<String?>(null)
    val recordingLocationError: StateFlow<String?> = _recordingLocationError.asStateFlow()

    init {
        recording.restore(loadRecordingSession())
        scope.launch {
            settings.collect { preferences.save(it) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }

    fun saveRecordingSession(session: RecordingSession?) {
        if (session == null) {
            recordingStore.delete(RECORDING_FILE)
        } else {
            recordingStore.writeValue(RECORDING_FILE, RecordingSession.serializer(), session)
        }
    }

    fun loadRecordingSession(): RecordingSession? =
        recordingStore.readValue(RECORDING_FILE, RecordingSession.serializer())

    fun recordLocationError(message: String?) {
        _recordingLocationError.value = message
    }

    companion object {
        const val RECORDING_FILE = "recording_session.json"
    }
}

object SystemTime {
    fun now(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}

object Ids {
    private var counter = 0L
    fun next(): String {
        counter += 1
        return com.trailbook.core.format.Str.fmt("%d-%d", SystemTime.now(), counter)
    }
}
