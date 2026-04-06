package com.lumus.vkapp.domain

import com.lumus.vkapp.transport.ProxySessionConfig

data class ResolvedConnectionPlan(
    val profile: ConnectionProfile,
    val proxySessionConfig: ProxySessionConfig,
    val runtimeWireGuardConfig: String,
)

