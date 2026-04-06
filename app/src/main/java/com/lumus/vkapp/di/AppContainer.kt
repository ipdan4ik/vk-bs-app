package com.lumus.vkapp.di

import android.app.Application
import androidx.room.Room
import com.lumus.vkapp.data.local.AppDatabase
import com.lumus.vkapp.data.profile.DefaultProfileRepository
import com.lumus.vkapp.data.profile.ProfileRepository
import com.lumus.vkapp.data.profile.ProfileSecretStore
import com.lumus.vkapp.data.settings.AppSettingsRepository
import com.lumus.vkapp.domain.ConnectionOrchestrator
import com.lumus.vkapp.domain.ConnectionOrchestratorImpl
import com.lumus.vkapp.transport.AndroidProxyManager
import com.lumus.vkapp.transport.ProxyManager
import com.lumus.vkapp.wireguard.AndroidWireGuardTunnelManager
import com.lumus.vkapp.wireguard.WireGuardTunnelManager

class AppContainer(
    application: Application,
) {
    private val database: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "lumus_vk_app.db",
    ).build()

    private val secretStore = ProfileSecretStore(application)

    val settingsRepository = AppSettingsRepository(application)

    val profileRepository: ProfileRepository = DefaultProfileRepository(
        profileDao = database.profileDao(),
        secretStore = secretStore,
    )

    val proxyManager: ProxyManager = AndroidProxyManager(application)

    val wireGuardTunnelManager: WireGuardTunnelManager = AndroidWireGuardTunnelManager(application)

    val connectionOrchestrator: ConnectionOrchestrator = ConnectionOrchestratorImpl(
        repository = profileRepository,
        proxyManager = proxyManager,
        wireGuardTunnelManager = wireGuardTunnelManager,
    )
}
