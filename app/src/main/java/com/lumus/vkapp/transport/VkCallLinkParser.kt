package com.lumus.vkapp.transport

import java.net.URI

object VkCallLinkParser {
    fun parse(rawLink: String): ParsedCallLink {
        val trimmed = rawLink.trim()
        require(trimmed.isNotEmpty()) { "Call link is required" }

        val uri = runCatching { URI(trimmed) }
            .getOrElse { throw IllegalArgumentException("Call link is not a valid URI") }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Call link host is missing")
        val scheme = uri.scheme?.lowercase() ?: throw IllegalArgumentException("Call link scheme is missing")

        require(scheme == "http" || scheme == "https") { "Call link must use http or https" }

        val provider = when {
            host.contains("vk.com") || host.contains("vkvideo.ru") -> CallProvider.VK
            host.contains("yandex") || host.contains("telemost") -> CallProvider.YANDEX
            else -> throw IllegalArgumentException("Only VK and Yandex call links are supported")
        }
        return ParsedCallLink(provider = provider, normalizedLink = uri.toString())
    }
}

