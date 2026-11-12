# TrailBook

An offline-first outdoor tracking app built with **Kotlin** and **Compose Multiplatform**. One shared Kotlin codebase contains all domain logic, persistence, integrations, and UI; platform-specific capabilities (GPS, sensors, secure storage, notifications, file pickers, sharing) live behind expect/actual adapters.

## Targets

| Target | Status | Notes |
|---|---|---|
| Android (minSdk 26, targetSdk 34) | Built and verified | Debug APK assembles; background recording via foreground service |
| iOS (x64, arm64, simulator arm64) | Code complete | Compiles into static framework `ComposeApp`; not compiled in this sandbox (no Kotlin/Native toolchain on Linux) |
| Desktop (JVM) | Built and verified | `./gradlew :composeApp:run`; needs a display + libGL (see limitations) |

## Build and test

Requires JDK 17.

```bash
./gradlew :composeApp:compileKotlinJvm          # desktop compile
./gradlew :composeApp:compileDebugKotlinAndroid # Android compile
./gradlew :composeApp:assembleDebug             # Android debug APK -> composeApp/build/outputs/apk/debug/
./gradlew :composeApp:jvmTest                   # unit tests (84 tests)
```

## Architecture

```
composeApp/src/
  commonMain/kotlin/com/trailbook/
    core/          pure-Kotlin domain: geo math, UTM, coordinate parsing, sunrise/sunset,
                   statistics, recording controller, route follower, routing client,
                   weather client, geocoding, GPX/KML/KMZ/TCX/CSV/FIT codecs, unit formatting
    data/          repository interfaces + JSON file persistence (JsonFileStore)
    platform/      expect declarations: file system, secure storage, HTTP transport,
                   location, sensors, permissions, notifications, sharing, file picker,
                   image codec, zip, connectivity, feedback (sound/vibration)
    app/           AppContainer (manual DI), settings state, recording session persistence
    app/ui/        Compose Multiplatform UI (Material 3): map, record, tracks, routes,
                   navigation, markers, weather, sensors, account, settings
  androidMain/     Android actuals (Keystore-backed SecureStore, LocationManager, SensorManager,
                   foreground service, SAF document picker, NotificationManager)
  jvmMain/         Desktop actuals (AES-GCM file SecureStore, Swing file chooser, no-op sensors)
  iosMain/         iOS actuals (Keychain SecureStore, CLLocationManager, CoreMotion,
                   NSURLSession, UIDocumentPicker, UIActivityViewController, UNUserNotificationCenter)
  commonTest/      84 unit tests for statistics, recording, route following, formats,
                   geo/UTM, units, persistence, sync, weather parsing, geocoding
```

Domain logic (`core/`) has no Compose dependencies; calculations and format parsing/writing are plain Kotlin and covered by tests. The UI calls repositories and services through `AppContainer` and never duplicates business logic.

## Offline-first

All tracks, routes, markers, marker lists, and settings are stored locally as JSON files and every CRUD workflow works fully offline. Tile maps use a memory + disk cache. Routing uses OSRM when online and falls back to a straight-line route with a clear notice when offline. Elevation lookup, geocoding search, and weather show explicit "unavailable/offline" states instead of fake data. Recording state is persisted to `recording_session.json` after every change and restored after process restart.

## Sync and integrations

- **Account/sync**: sign-in with server URL, session persisted in platform secure storage (Android Keystore / iOS Keychain / encrypted file on desktop). `SyncEngine` uploads modified tracks and markers, downloads and merges remote ones, detects 409 conflicts, and reports `Offline` / `Failure` / `NotSignedIn` outcomes. The default server URL is a placeholder — point it at any compatible endpoint.
- **Suunto**: OAuth2 token flow, workout listing, and FIT download via `SuuntoService`. OAuth client credentials are empty constants in `SuuntoService.kt` (no secrets are committed); supply them there or via build config to enable the integration. Without credentials the Settings screen shows an explicit "not configured" message.
- **Purchases**: `BillingAdapter` interface with a safe **test-mode** implementation (unlock/restore toggles a local entitlement) since no store account is available in this environment.

## Feature checklist

- [x] Interactive 2D map with pan/zoom gestures, tile styles: standard, satellite, terrain, light, dark (`core/prefs/Settings.kt` `MapStyle`, `app/ui/map/`)
- [x] Map layers: elevation, slope angle, aspect, min/max elevation, flat areas — computed from a real elevation grid (Open-Meteo elevation API); avalanche risk shows an explicit "no free data source" notice
- [x] Overlays: user location, tracks, routes, markers, weather layer toggle
- [x] Search by place name (Nominatim) and by coordinates: decimal degrees, degrees-minutes, DMS, UTM; recent searches preserved
- [x] Selected-location card: coordinate display in configured format, elevation lookup, sunrise/sunset, add marker
- [x] Map snapshot export: real rendered PNG of visible tiles + tracks/routes/markers, saved via platform share (`MapScreen.exportMapSnapshot`)
- [x] GPS track recording: start/pause/resume/stop/save/discard, 15 activity types, accuracy/distance/interval filtering, session persisted across restarts, foreground service + ongoing notification on Android
- [x] Track statistics: distance, total/moving time, avg/max/moving-avg speed, elevation profile, gain/loss with smoothing and threshold (`core/stats/StatisticsEngine`)
- [x] Track details: stats grid, elevation/speed charts, metadata editing, delete, use-as-route, export
- [x] Track management: list, sort, filter by activity type, multi-select, bulk delete, import
- [x] Route planning: OSRM routing with activity profiles (hiking/cycling/driving…), straight-line offline fallback, route from existing track, save route as track, distance/elevation info
- [x] Route following: live progress, remaining distance, off-route/back-on-route events, waypoint/completion guidance, sound + vibration via platform feedback adapter
- [x] Import: GPX, KML, KMZ, TCX, CSV, FIT with validation, warnings, waypoints; multi-track files
- [x] Export: GPX (with waypoints), KML, KMZ, TCX, CSV; sharing via platform share sheet / Downloads
- [x] Markers and marker lists: CRUD, 16 icon choices, list organization, KML export/share of markers
- [x] Weather: hourly/daily forecasts (Open-Meteo), model selection (ECMWF/GFS/ICON/Météo-France/UKMO), freezing level, cloud base, snowfall, moon phase, weather codes
- [x] Live sensors: compass, speed, altitude, pressure, tilt, magnetic field with explicit unavailable/permission states
- [x] Account/profile, track + marker sync with conflict reporting, signed-out behavior
- [x] Suunto connect/disconnect, workout list, workout import as track
- [x] Settings: units (metric/imperial), theme, map style, coordinate format, recording, chart, weather, navigation preferences; persisted
- [x] Purchase/restore with test mode; entitlement checked in settings model
- [x] About, licenses, privacy, terms, contact links in Settings

## Known limitations

1. **iOS not compiled here** — this Linux sandbox has no Kotlin/Native toolchain. The iOS actuals (Keychain, CoreLocation, CoreMotion, NSURLSession, document picker) are written against the standard platform APIs but were never compiler-verified. What is needed: macOS with Xcode to run `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` and an `iosApp` Xcode shell project (not included).
2. **Desktop runtime needs libGL** — the app compiles and launches but Skia requires `libGL.so.1`, which this headless sandbox cannot install. On a normal desktop with OpenGL it runs via `./gradlew :composeApp:run`.
3. **3D map view** — only 2D is implemented; a 3D terrain view would require a terrain tile source (e.g. Terrarium/Mapzen DEM) and 3D mesh rendering.
4. **Avalanche-risk layer** — no free public avalanche-bulletin API exists; the layer shows an explicit limitation notice instead of fabricated data.
5. **Downhill-difficulty layer** — no evidence of this layer in the (empty) source repository; ski-piste difficulty would require an OSM piste-map overlay.
6. **Suunto integration** — requires OAuth client credentials registered with Suunto; without them the connect flow reports "not configured". No success is claimed.
7. **Sync server** — the account/sync server URL is user-provided; no reference backend exists in the (empty) source repository. Protocol: `PUT/GET /tracks`, `PUT/GET /markers`, `POST /auth/login` with bearer tokens.
8. **Purchases** — real store billing (Play Billing / StoreKit) needs store accounts; a test-mode adapter is used instead.
9. **FIT export** — FIT is import-only (spec-conformant FIT writing is out of scope); export offers GPX/KML/KMZ/TCX/CSV.
