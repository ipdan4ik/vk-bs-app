package com.lumus.vkapp.domain

import kotlinx.coroutines.flow.Flow

interface ConnectionOrchestrator {
    suspend fun connect(profileId: Long)
    suspend fun disconnect(profileId: Long)
    fun observeState(profileId: Long): Flow<ConnectionState>
    suspend fun fetchDiagnostics(profileId: Long): List<String>
}

