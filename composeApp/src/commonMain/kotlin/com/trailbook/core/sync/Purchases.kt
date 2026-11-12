package com.trailbook.core.sync

import com.trailbook.core.prefs.PreferencesRepository

interface BillingAdapter {
    val isTestMode: Boolean
    suspend fun purchase(): PurchaseResult
    suspend fun restore(): PurchaseResult
}

sealed interface PurchaseResult {
    data class Unlocked(val restored: Boolean, val testMode: Boolean) : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

class TestModeBillingAdapter(private val preferences: PreferencesRepository) : BillingAdapter {
    override val isTestMode: Boolean get() = true

    override suspend fun purchase(): PurchaseResult {
        val settings = preferences.load()
        preferences.save(settings.copy(fullVersionUnlocked = true))
        return PurchaseResult.Unlocked(restored = false, testMode = true)
    }

    override suspend fun restore(): PurchaseResult {
        val settings = preferences.load()
        preferences.save(settings.copy(fullVersionUnlocked = true))
        return PurchaseResult.Unlocked(restored = true, testMode = true)
    }
}
