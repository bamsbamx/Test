package com.trailbook.core.prefs

import com.trailbook.data.JsonFileStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface PreferencesRepository {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

class FilePreferencesRepository(private val store: JsonFileStore) : PreferencesRepository {
    private val serializer: KSerializer<AppSettings> = serializer()

    @Synchronized
    override fun load(): AppSettings = store.readValue(FILE, serializer) ?: AppSettings()

    @Synchronized
    override fun save(settings: AppSettings) {
        store.writeValue(FILE, serializer, settings)
    }

    companion object { const val FILE = "settings.json" }
}
