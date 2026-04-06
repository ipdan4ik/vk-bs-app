package com.lumus.vkapp.data.profile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lumus.vkapp.domain.WireGuardProfileSource

data class ProfileSecrets(
    val wireGuardSource: WireGuardProfileSource,
    val wireGuardConfig: String,
    val callLink: String,
)

class ProfileSecretStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "profile_secrets",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(profileId: Long, secrets: ProfileSecrets) {
        prefs.edit()
            .putString("$profileId.wgSourceType", secrets.wireGuardSource::class.simpleName)
            .putString("$profileId.wgSourceName", sourceName(secrets.wireGuardSource))
            .putString("$profileId.wgConfig", secrets.wireGuardConfig)
            .putString("$profileId.callLink", secrets.callLink)
            .apply()
    }

    fun read(profileId: Long): ProfileSecrets? {
        val wireGuardConfig = prefs.getString("$profileId.wgConfig", null) ?: return null
        val callLink = prefs.getString("$profileId.callLink", null) ?: return null
        val sourceType = prefs.getString("$profileId.wgSourceType", null)
        val sourceName = prefs.getString("$profileId.wgSourceName", null).orEmpty()
        return ProfileSecrets(
            wireGuardSource = when (sourceType) {
                WireGuardProfileSource.FileImport::class.simpleName ->
                    WireGuardProfileSource.FileImport(sourceName, wireGuardConfig)
                WireGuardProfileSource.QrImport::class.simpleName ->
                    WireGuardProfileSource.QrImport(wireGuardConfig)
                else -> WireGuardProfileSource.RawText(wireGuardConfig)
            },
            wireGuardConfig = wireGuardConfig,
            callLink = callLink,
        )
    }

    fun delete(profileId: Long) {
        prefs.edit()
            .remove("$profileId.wgSourceType")
            .remove("$profileId.wgSourceName")
            .remove("$profileId.wgConfig")
            .remove("$profileId.callLink")
            .apply()
    }

    private fun sourceName(source: WireGuardProfileSource): String = when (source) {
        is WireGuardProfileSource.FileImport -> source.fileName
        else -> ""
    }
}

