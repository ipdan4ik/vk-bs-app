package com.lumus.vkapp.domain

import com.lumus.vkapp.BuildConfig
import com.lumus.vkapp.data.profile.ProfileRepository
import com.lumus.vkapp.transport.ProxyManager
import com.lumus.vkapp.transport.ProxyRuntimeState
import com.lumus.vkapp.transport.ProxySessionConfig
import com.lumus.vkapp.transport.VkCallLinkParser
import com.lumus.vkapp.wireguard.WireGuardConfigRewriter
import com.lumus.vkapp.wireguard.WireGuardTunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ConnectionOrchestratorImpl(
    private val repository: ProfileRepository,
    private val proxyManager: ProxyManager,
    private val wireGuardTunnelManager: WireGuardTunnelManager,
) : ConnectionOrchestrator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private var activeProfileId: Long? = null
    private var activeTunnelName: String? = null
    private var monitorJob: Job? = null

    override suspend fun connect(profileId: Long) {
        val profile = repository.getProfile(profileId)
            ?: error("Connection profile $profileId was not found")

        if (activeProfileId != null) {
            disconnect(activeProfileId!!)
        } else {
            monitorJob?.cancelAndJoin()
            monitorJob = null
            activeProfileId = null
            activeTunnelName = null
            state.value = ConnectionState.Idle
        }
        state.value = ConnectionState.Validating(profileId)
        val plan = resolvePlan(profile)

        state.value = ConnectionState.StartingProxy(profileId)
        proxyManager.start(plan.proxySessionConfig)
        val proxyState = withTimeoutOrNull(30_000) {
            proxyManager.state
                .filter { it !is ProxyRuntimeState.Idle && it !is ProxyRuntimeState.Stopping }
                .first { it is ProxyRuntimeState.Ready || it is ProxyRuntimeState.Failed }
        }
        when {
            proxyState == null -> {
                state.value = ConnectionState.Error(profileId, "Proxy did not start within 30 seconds")
                return
            }
            proxyState is ProxyRuntimeState.Failed -> {
                state.value = ConnectionState.Error(profileId, proxyState.message)
                return
            }
        }

        state.value = ConnectionState.StartingVpn(profileId)
        runCatching {
            val tunnelName = profile.name.slugifyTunnelName()
            wireGuardTunnelManager.connect(tunnelName, plan.runtimeWireGuardConfig)
            activeProfileId = profileId
            activeTunnelName = tunnelName
            state.value = ConnectionState.Connected(profileId, tunnelName)
            monitorProxyHealth(profileId)
        }.getOrElse { throwable ->
            proxyManager.stop()
            state.value = ConnectionState.Error(profileId, throwable.message ?: "Failed to start WireGuard")
        }
    }

    override suspend fun disconnect(profileId: Long) {
        val currentProfileId = activeProfileId ?: if (state.value is ConnectionState.Error) profileId else null
        if (currentProfileId != null) {
            state.value = ConnectionState.Stopping(currentProfileId)
        }
        monitorJob?.cancelAndJoin()
        monitorJob = null
        runCatching { wireGuardTunnelManager.disconnect() }
        if (proxyManager.state.value !is ProxyRuntimeState.Idle) {
            runCatching { proxyManager.stop() }
        }
        activeProfileId = null
        activeTunnelName = null
        state.value = ConnectionState.Idle
    }

    override fun observeState(profileId: Long): Flow<ConnectionState> {
        return state.map { current ->
            when (current) {
                is ConnectionState.Validating -> if (current.profileId == profileId) current else ConnectionState.Idle
                is ConnectionState.StartingProxy -> if (current.profileId == profileId) current else ConnectionState.Idle
                is ConnectionState.StartingVpn -> if (current.profileId == profileId) current else ConnectionState.Idle
                is ConnectionState.Connected -> if (current.profileId == profileId) current else ConnectionState.Idle
                is ConnectionState.Stopping -> if (current.profileId == profileId) current else ConnectionState.Idle
                is ConnectionState.Error -> if (current.profileId == null || current.profileId == profileId) current else ConnectionState.Idle
                ConnectionState.Idle -> ConnectionState.Idle
            }
        }
    }

    override suspend fun fetchDiagnostics(profileId: Long): List<String> {
        val version = runCatching { wireGuardTunnelManager.version() }.getOrDefault("unknown")
        return buildList {
            add("profileId=$profileId")
            add("wireguardVersion=$version")
            add("activeTunnel=${activeTunnelName ?: "none"}")
            add("proxyState=${proxyManager.state.value}")
            addAll(proxyManager.logs.value)
        }
    }

    private suspend fun resolvePlan(profile: ConnectionProfile): ResolvedConnectionPlan {
        require(profile.name.isNotBlank()) { "Profile name is required" }
        require(profile.relayHost.isNotBlank()) { "Relay host is required" }
        require(profile.relayPort in 1..65535) { "Relay port is invalid" }
        require(profile.localProxyPort in 1..65535) { "Local proxy port is invalid" }
        require(profile.workerCount > 0) { "Worker count must be positive" }
        require(wireGuardTunnelManager.getVpnPermissionIntent() == null) { "VPN permission is not granted" }

        val parsedCallLink = VkCallLinkParser.parse(profile.callLink)
        val runtimeWireGuardConfig = WireGuardConfigRewriter.rewrite(
            originalConfig = profile.wireGuardConfig,
            localHost = "127.0.0.1",
            localPort = profile.localProxyPort,
            mtuOverride = profile.mtu,
            dnsOverride = profile.dnsServers,
            keepaliveOverride = profile.keepaliveSeconds,
            ignoredApplications = setOf(BuildConfig.APPLICATION_ID),
        )
        return ResolvedConnectionPlan(
            profile = profile,
            proxySessionConfig = ProxySessionConfig(
                relayPeer = "${profile.relayHost}:${profile.relayPort}",
                callLink = parsedCallLink.normalizedLink,
                callProvider = parsedCallLink.provider,
                localListenPort = profile.localProxyPort,
                workerCount = profile.workerCount,
                useUdp = profile.useUdp,
                disableDtls = profile.disableDtls,
            ),
            runtimeWireGuardConfig = runtimeWireGuardConfig,
        )
    }

    private fun monitorProxyHealth(profileId: Long) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            proxyManager.state.collect { proxyState ->
                if (proxyState is ProxyRuntimeState.Failed && activeProfileId == profileId) {
                    runCatching { wireGuardTunnelManager.disconnect() }
                    state.value = ConnectionState.Error(profileId, proxyState.message)
                    activeProfileId = null
                    activeTunnelName = null
                }
            }
        }
    }
}

private fun String.slugifyTunnelName(): String {
    val cleaned = lowercase()
        .replace(Regex("[^a-z0-9_=+.-]+"), "-")
        .trim('-')
        .take(15)
    return cleaned.ifBlank { "lumus" }
}
