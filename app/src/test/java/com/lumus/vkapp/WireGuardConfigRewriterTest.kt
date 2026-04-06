package com.lumus.vkapp

import com.lumus.vkapp.wireguard.WireGuardConfigRewriter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardConfigRewriterTest {
    @Test
    fun `rewrites endpoint without mutating original config`() {
        val rewritten = WireGuardConfigRewriter.rewrite(
            originalConfig = sampleWireGuardConfig,
            localHost = "127.0.0.1",
            localPort = 9000,
            mtuOverride = 1280,
            dnsOverride = listOf("9.9.9.9"),
            keepaliveOverride = 25,
            ignoredApplications = setOf("com.lumus.vkapp"),
        )

        assertTrue(rewritten.contains("Endpoint = 127.0.0.1:9000"))
        assertTrue(rewritten.contains("PersistentKeepalive = 25"))
        assertTrue(rewritten.contains("MTU = 1280"))
        assertTrue(rewritten.contains("DNS = 9.9.9.9"))
        assertTrue(rewritten.contains("ExcludedApplications = com.lumus.vkapp"))
        assertFalse(sampleWireGuardConfig.contains("127.0.0.1:9000"))
    }

    @Test
    fun `removes ignored applications from included applications lists`() {
        val rewritten = WireGuardConfigRewriter.rewrite(
            originalConfig = sampleWireGuardConfigWithIncludedApps,
            localHost = "127.0.0.1",
            localPort = 9000,
            mtuOverride = null,
            dnsOverride = emptyList(),
            keepaliveOverride = null,
            ignoredApplications = setOf("com.lumus.vkapp"),
        )

        assertTrue(rewritten.contains("IncludedApplications = com.example.browser"))
        assertFalse(rewritten.contains("IncludedApplications = com.lumus.vkapp"))
        assertFalse(rewritten.contains("ExcludedApplications = com.lumus.vkapp"))
    }
}
