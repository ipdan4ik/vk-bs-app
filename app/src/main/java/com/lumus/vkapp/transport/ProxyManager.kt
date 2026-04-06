package com.lumus.vkapp.transport

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.StateFlow

sealed interface ProxyRuntimeState {
    data object Idle : ProxyRuntimeState
    data object Starting : ProxyRuntimeState
    data object Ready : ProxyRuntimeState
    data object Stopping : ProxyRuntimeState
    data class Failed(val message: String) : ProxyRuntimeState
}

interface ProxyManager {
    val state: StateFlow<ProxyRuntimeState>
    val logs: StateFlow<List<String>>
    suspend fun start(config: ProxySessionConfig)
    suspend fun stop()
}

class AndroidProxyManager(
    private val context: Context,
) : ProxyManager {
    override val state: StateFlow<ProxyRuntimeState> = VkTurnProxyService.state
    override val logs: StateFlow<List<String>> = VkTurnProxyService.logs

    override suspend fun start(config: ProxySessionConfig) {
        val intent = Intent(context, VkTurnProxyService::class.java).apply {
            action = VkTurnProxyService.ACTION_START
            putExtra(VkTurnProxyService.EXTRA_RELAY_PEER, config.relayPeer)
            putExtra(VkTurnProxyService.EXTRA_CALL_LINK, config.callLink)
            putExtra(VkTurnProxyService.EXTRA_CALL_PROVIDER, config.callProvider.name)
            putExtra(VkTurnProxyService.EXTRA_LOCAL_HOST, config.localListenHost)
            putExtra(VkTurnProxyService.EXTRA_LOCAL_PORT, config.localListenPort)
            putExtra(VkTurnProxyService.EXTRA_WORKERS, config.workerCount)
            putExtra(VkTurnProxyService.EXTRA_USE_UDP, config.useUdp)
            putExtra(VkTurnProxyService.EXTRA_DISABLE_DTLS, config.disableDtls)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override suspend fun stop() {
        context.startService(
            Intent(context, VkTurnProxyService::class.java).apply {
                action = VkTurnProxyService.ACTION_STOP
            },
        )
    }
}

