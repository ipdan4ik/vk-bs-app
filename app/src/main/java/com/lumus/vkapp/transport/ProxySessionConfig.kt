package com.lumus.vkapp.transport

enum class CallProvider {
    VK,
    YANDEX,
}

data class ParsedCallLink(
    val provider: CallProvider,
    val normalizedLink: String,
)

data class ProxySessionConfig(
    val relayPeer: String,
    val callLink: String,
    val callProvider: CallProvider,
    val localListenHost: String = "127.0.0.1",
    val localListenPort: Int,
    val workerCount: Int,
    val useUdp: Boolean,
    val disableDtls: Boolean,
)

