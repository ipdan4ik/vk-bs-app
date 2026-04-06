package com.lumus.vkapp

import android.content.Intent
import com.lumus.vkapp.data.profile.ProfileRepository
import com.lumus.vkapp.domain.ConnectionOrchestrator
import com.lumus.vkapp.domain.ConnectionOrchestratorImpl
import com.lumus.vkapp.domain.ConnectionProfile
import com.lumus.vkapp.domain.ConnectionState
import com.lumus.vkapp.domain.WireGuardProfileSource
import com.lumus.vkapp.transport.ProxyManager
import com.lumus.vkapp.transport.ProxyRuntimeState
import com.lumus.vkapp.transport.ProxySessionConfig
import com.lumus.vkapp.wireguard.WireGuardTunnelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionOrchestratorImplTest {
    @Test
    fun `connects after proxy becomes ready`() = runTest {
        val repository = FakeProfileRepository()
        val proxyManager = FakeProxyManager(ProxyRuntimeState.Ready)
        val wireGuard = FakeWireGuardTunnelManager()
        val orchestrator: ConnectionOrchestrator = ConnectionOrchestratorImpl(repository, proxyManager, wireGuard)

        orchestrator.connect(repository.profile.id)

        val state = orchestrator.observeState(repository.profile.id).first()
        assertTrue(state is ConnectionState.Connected)
        assertEquals("lumus-home", wireGuard.connectedTunnelName)
        assertTrue(wireGuard.connectedConfig?.contains("Endpoint = 127.0.0.1:9000") == true)
    }

    @Test
    fun `surfaces proxy startup failure`() = runTest {
        val repository = FakeProfileRepository()
        val proxyManager = FakeProxyManager(ProxyRuntimeState.Failed("proxy down"))
        val wireGuard = FakeWireGuardTunnelManager()
        val orchestrator = ConnectionOrchestratorImpl(repository, proxyManager, wireGuard)

        orchestrator.connect(repository.profile.id)

        val finalState = orchestrator.observeState(repository.profile.id).first()
        assertTrue(finalState is ConnectionState.Error)
        assertEquals(null, wireGuard.connectedTunnelName)
        assertEquals(ProxyRuntimeState.Failed("proxy down"), proxyManager.state.value)
    }
}

private class FakeProfileRepository : ProfileRepository {
    val profile = ConnectionProfile(
        id = 7,
        name = "Lumus Home",
        wireGuardSource = WireGuardProfileSource.RawText(sampleWireGuardConfig),
        wireGuardConfig = sampleWireGuardConfig,
        callLink = "https://vk.com/call/join/abcdef",
        relayHost = "relay.example.com",
        relayPort = 443,
        localProxyPort = 9000,
        mtu = 1280,
        dnsServers = listOf("1.1.1.1"),
        keepaliveSeconds = 25,
        workerCount = 8,
        useUdp = true,
        disableDtls = false,
    )

    override fun observeProfiles(): Flow<List<ConnectionProfile>> = flowOf(listOf(profile))

    override suspend fun getProfile(id: Long): ConnectionProfile? = profile.takeIf { it.id == id }

    override suspend fun saveProfile(profile: ConnectionProfile): Long = profile.id

    override suspend fun deleteProfile(profileId: Long) = Unit
}

private class FakeProxyManager(
    initialResult: ProxyRuntimeState,
) : ProxyManager {
    override val state: MutableStateFlow<ProxyRuntimeState> = MutableStateFlow(ProxyRuntimeState.Idle)
    override val logs: StateFlow<List<String>> = MutableStateFlow(emptyList())
    private val nextState = initialResult

    override suspend fun start(config: ProxySessionConfig) {
        state.value = nextState
    }

    override suspend fun stop() {
        state.value = ProxyRuntimeState.Idle
    }
}

private class FakeWireGuardTunnelManager : WireGuardTunnelManager {
    var connectedTunnelName: String? = null
    var connectedConfig: String? = null

    override fun getVpnPermissionIntent(): Intent? = null

    override suspend fun connect(tunnelName: String, configText: String) {
        connectedTunnelName = tunnelName
        connectedConfig = configText
    }

    override suspend fun disconnect() {
        connectedTunnelName = null
        connectedConfig = null
    }

    override suspend fun version(): String = "fake-wg"
}
