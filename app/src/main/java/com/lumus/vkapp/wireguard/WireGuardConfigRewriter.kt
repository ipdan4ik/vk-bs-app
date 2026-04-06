package com.lumus.vkapp.wireguard

import com.wireguard.config.Config
import com.wireguard.config.InetAddresses
import com.wireguard.config.InetEndpoint
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.io.ByteArrayInputStream

object WireGuardConfigRewriter {
    fun rewrite(
        originalConfig: String,
        localHost: String,
        localPort: Int,
        mtuOverride: Int?,
        dnsOverride: List<String>,
        keepaliveOverride: Int?,
        ignoredApplications: Set<String> = emptySet(),
    ): String {
        val parsed = Config.parse(ByteArrayInputStream(originalConfig.toByteArray()))
        val wgInterface = parsed.`interface`
        val includedApplications = wgInterface.includedApplications - ignoredApplications
        val excludedApplications = if (includedApplications.isEmpty()) {
            wgInterface.excludedApplications + ignoredApplications
        } else {
            wgInterface.excludedApplications
        }

        val interfaceBuilder = Interface.Builder()
            .setKeyPair(wgInterface.keyPair)
            .addAddresses(wgInterface.addresses)
            .includeApplications(includedApplications)
            .excludeApplications(excludedApplications)

        wgInterface.listenPort.ifPresent { interfaceBuilder.setListenPort(it) }
        (mtuOverride ?: wgInterface.mtu.orElse(null))?.let { interfaceBuilder.setMtu(it) }

        if (dnsOverride.isNotEmpty()) {
            dnsOverride.forEach { dnsValue ->
                runCatching { InetAddresses.parse(dnsValue) }
                    .onSuccess { interfaceBuilder.addDnsServer(it) }
                    .onFailure { interfaceBuilder.addDnsSearchDomain(dnsValue) }
            }
        } else {
            interfaceBuilder.addDnsServers(wgInterface.dnsServers)
            interfaceBuilder.addDnsSearchDomains(wgInterface.dnsSearchDomains)
        }

        val proxyEndpoint = InetEndpoint.parse("$localHost:$localPort")
        val peers = parsed.peers.map { peer ->
            val builder = Peer.Builder()
                .setPublicKey(peer.publicKey)
                .addAllowedIps(peer.allowedIps)
                .setEndpoint(proxyEndpoint)

            peer.preSharedKey.ifPresent { builder.setPreSharedKey(it) }
            (keepaliveOverride ?: peer.persistentKeepalive.orElse(null))?.let {
                builder.setPersistentKeepalive(it)
            }
            builder.build()
        }

        return Config.Builder()
            .setInterface(interfaceBuilder.build())
            .addPeers(peers)
            .build()
            .toWgQuickString()
    }
}
