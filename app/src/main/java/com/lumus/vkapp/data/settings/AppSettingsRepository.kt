package com.lumus.vkapp.data.settings

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("app_settings")

class AppSettingsRepository(
    private val context: Context,
) {
    private val selectedProfileKey = longPreferencesKey("selected_profile_id")

    val selectedProfileId: Flow<Long?> = context.settingsDataStore.data.map { prefs ->
        prefs[selectedProfileKey]
    }

    suspend fun setSelectedProfileId(profileId: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(selectedProfileKey)
            } else {
                prefs[selectedProfileKey] = profileId
            }
        }
    }
}

