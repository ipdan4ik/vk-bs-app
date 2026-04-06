package com.lumus.vkapp.domain

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Validating(val profileId: Long) : ConnectionState
    data class StartingProxy(val profileId: Long) : ConnectionState
    data class StartingVpn(val profileId: Long) : ConnectionState
    data class Connected(val profileId: Long, val tunnelName: String) : ConnectionState
    data class Stopping(val profileId: Long) : ConnectionState
    data class Error(val profileId: Long?, val message: String) : ConnectionState
}

