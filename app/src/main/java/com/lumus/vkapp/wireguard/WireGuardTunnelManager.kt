package com.lumus.vkapp.wireguard

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

interface WireGuardTunnelManager {
    fun getVpnPermissionIntent(): Intent?
    suspend fun connect(tunnelName: String, configText: String)
    suspend fun disconnect()
    suspend fun version(): String
}

class AndroidWireGuardTunnelManager(
    private val context: Context,
) : WireGuardTunnelManager {
    private val backend by lazy { GoBackend(context.applicationContext) }
    private var activeTunnel: AppTunnel? = null

    override fun getVpnPermissionIntent(): Intent? = VpnService.prepare(context)

    override suspend fun connect(tunnelName: String, configText: String) {
        disconnect()
        val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))
        val tunnel = AppTunnel(tunnelName)
        backend.setState(tunnel, Tunnel.State.UP, config)
        activeTunnel = tunnel
    }

    override suspend fun disconnect() {
        val tunnel = activeTunnel ?: return
        backend.setState(tunnel, Tunnel.State.DOWN, null)
        activeTunnel = null
    }

    override suspend fun version(): String = backend.version
}

private class AppTunnel(
    private val tunnelName: String,
) : Tunnel {
    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) = Unit
}

