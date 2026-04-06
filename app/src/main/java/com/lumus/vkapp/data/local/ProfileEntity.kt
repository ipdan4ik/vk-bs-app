package com.lumus.vkapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relayHost: String,
    val relayPort: Int,
    val localProxyPort: Int,
    val mtu: Int?,
    val dnsServersCsv: String,
    val keepaliveSeconds: Int?,
    val workerCount: Int,
    val useUdp: Boolean,
    val disableDtls: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

