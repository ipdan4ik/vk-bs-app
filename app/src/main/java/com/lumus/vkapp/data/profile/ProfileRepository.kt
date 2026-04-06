package com.lumus.vkapp.data.profile

import com.lumus.vkapp.data.local.ProfileDao
import com.lumus.vkapp.data.local.ProfileEntity
import com.lumus.vkapp.domain.ConnectionProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ProfileRepository {
    fun observeProfiles(): Flow<List<ConnectionProfile>>
    suspend fun getProfile(id: Long): ConnectionProfile?
    suspend fun saveProfile(profile: ConnectionProfile): Long
    suspend fun deleteProfile(profileId: Long)
}

class DefaultProfileRepository(
    private val profileDao: ProfileDao,
    private val secretStore: ProfileSecretStore,
) : ProfileRepository {
    override fun observeProfiles(): Flow<List<ConnectionProfile>> {
        return profileDao.observeAll().map { entities ->
            entities.mapNotNull { entity ->
                secretStore.read(entity.id)?.let { secrets -> entity.toDomain(secrets) }
            }
        }
    }

    override suspend fun getProfile(id: Long): ConnectionProfile? {
        val entity = profileDao.getById(id) ?: return null
        val secrets = secretStore.read(entity.id) ?: return null
        return entity.toDomain(secrets)
    }

    override suspend fun saveProfile(profile: ConnectionProfile): Long {
        val savedId = profileDao.save(profile.toEntity())
        val resolvedId = if (profile.id == 0L) savedId else profile.id
        secretStore.save(
            resolvedId,
            ProfileSecrets(
                wireGuardSource = profile.wireGuardSource,
                wireGuardConfig = profile.wireGuardConfig,
                callLink = profile.callLink,
            ),
        )
        return resolvedId
    }

    override suspend fun deleteProfile(profileId: Long) {
        profileDao.getById(profileId)?.let { profileDao.delete(it) }
        secretStore.delete(profileId)
    }
}

private fun ConnectionProfile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    relayHost = relayHost,
    relayPort = relayPort,
    localProxyPort = localProxyPort,
    mtu = mtu,
    dnsServersCsv = dnsServers.joinToString(","),
    keepaliveSeconds = keepaliveSeconds,
    workerCount = workerCount,
    useUdp = useUdp,
    disableDtls = disableDtls,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = System.currentTimeMillis(),
)

private fun ProfileEntity.toDomain(secrets: ProfileSecrets): ConnectionProfile = ConnectionProfile(
    id = id,
    name = name,
    wireGuardSource = secrets.wireGuardSource,
    wireGuardConfig = secrets.wireGuardConfig,
    callLink = secrets.callLink,
    relayHost = relayHost,
    relayPort = relayPort,
    localProxyPort = localProxyPort,
    mtu = mtu,
    dnsServers = dnsServersCsv.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) },
    keepaliveSeconds = keepaliveSeconds,
    workerCount = workerCount,
    useUdp = useUdp,
    disableDtls = disableDtls,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

