package com.lumus.vkapp.domain

sealed interface WireGuardProfileSource {
    data class RawText(val configText: String) : WireGuardProfileSource
    data class FileImport(val fileName: String, val configText: String) : WireGuardProfileSource
    data class QrImport(val configText: String) : WireGuardProfileSource
}

data class ConnectionProfile(
    val id: Long = 0,
    val name: String,
    val wireGuardSource: WireGuardProfileSource,
    val wireGuardConfig: String,
    val callLink: String,
    val relayHost: String,
    val relayPort: Int,
    val localProxyPort: Int,
    val mtu: Int?,
    val dnsServers: List<String>,
    val keepaliveSeconds: Int?,
    val workerCount: Int,
    val useUdp: Boolean,
    val disableDtls: Boolean,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

