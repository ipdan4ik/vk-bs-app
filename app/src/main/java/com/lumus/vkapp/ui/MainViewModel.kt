package com.lumus.vkapp.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumus.vkapp.LumusVpnApplication
import com.lumus.vkapp.data.settings.AppSettingsRepository
import com.lumus.vkapp.di.AppContainer
import com.lumus.vkapp.domain.ConnectionOrchestrator
import com.lumus.vkapp.domain.ConnectionProfile
import com.lumus.vkapp.domain.ConnectionState
import com.lumus.vkapp.domain.WireGuardProfileSource
import com.lumus.vkapp.transport.ProxyManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileEditorState(
    val id: Long = 0,
    val name: String = "",
    val relayHost: String = "",
    val relayPort: String = "443",
    val localProxyPort: String = "9000",
    val mtu: String = "",
    val dnsServers: String = "",
    val keepaliveSeconds: String = "",
    val workerCount: String = "8",
    val callLink: String = "",
    val wireGuardConfig: String = "",
    val wireGuardSource: WireGuardProfileSource = WireGuardProfileSource.RawText(""),
    val useUdp: Boolean = true,
    val disableDtls: Boolean = false,
)

data class MainUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val selectedProfileId: Long? = null,
    val selectedProfile: ConnectionProfile? = null,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val editorState: ProfileEditorState? = null,
    val diagnostics: List<String> = emptyList(),
    val transientMessage: String? = null,
)

private data class CoreUiState(
    val profiles: List<ConnectionProfile>,
    val selectedProfileId: Long?,
    val selectedProfile: ConnectionProfile?,
    val connectionState: ConnectionState,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContainer: AppContainer = (application as LumusVpnApplication).appContainer
    private val profileRepository = appContainer.profileRepository
    private val settingsRepository: AppSettingsRepository = appContainer.settingsRepository
    private val orchestrator: ConnectionOrchestrator = appContainer.connectionOrchestrator
    private val proxyManager: ProxyManager = appContainer.proxyManager
    private val wireGuardTunnelManager = appContainer.wireGuardTunnelManager

    private val editorState = MutableStateFlow<ProfileEditorState?>(null)
    private val transientMessage = MutableStateFlow<String?>(null)

    private val profiles = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedProfileId = settingsRepository.selectedProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedProfile = combine(profiles, selectedProfileId) { allProfiles, selectedId ->
        allProfiles.firstOrNull { it.id == selectedId } ?: allProfiles.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val connectionState = selectedProfile.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(ConnectionState.Idle)
        } else {
            orchestrator.observeState(profile.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Idle)

    private val coreUiState = combine(
        profiles,
        selectedProfileId,
        selectedProfile,
        connectionState,
    ) { allProfiles, selectedId, currentProfile, sessionState ->
        CoreUiState(
            profiles = allProfiles,
            selectedProfileId = selectedId ?: currentProfile?.id,
            selectedProfile = currentProfile,
            connectionState = sessionState,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CoreUiState(
            profiles = emptyList(),
            selectedProfileId = null,
            selectedProfile = null,
            connectionState = ConnectionState.Idle,
        ),
    )

    val uiState: StateFlow<MainUiState> = combine(
        coreUiState,
        editorState,
        proxyManager.logs,
        transientMessage,
    ) { coreState, editor, logs, message ->
        MainUiState(
            profiles = coreState.profiles,
            selectedProfileId = coreState.selectedProfileId,
            selectedProfile = coreState.selectedProfile,
            connectionState = coreState.connectionState,
            editorState = editor,
            diagnostics = logs,
            transientMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            profiles.collect { allProfiles ->
                val currentSelected = selectedProfileId.value
                if (currentSelected == null && allProfiles.isNotEmpty()) {
                    settingsRepository.setSelectedProfileId(allProfiles.first().id)
                }
            }
        }
    }

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            settingsRepository.setSelectedProfileId(profileId)
        }
    }

    fun showCreateProfile() {
        editorState.value = ProfileEditorState()
    }

    fun editProfile(profile: ConnectionProfile) {
        editorState.value = ProfileEditorState(
            id = profile.id,
            name = profile.name,
            relayHost = profile.relayHost,
            relayPort = profile.relayPort.toString(),
            localProxyPort = profile.localProxyPort.toString(),
            mtu = profile.mtu?.toString().orEmpty(),
            dnsServers = profile.dnsServers.joinToString(", "),
            keepaliveSeconds = profile.keepaliveSeconds?.toString().orEmpty(),
            workerCount = profile.workerCount.toString(),
            callLink = profile.callLink,
            wireGuardConfig = profile.wireGuardConfig,
            wireGuardSource = profile.wireGuardSource,
            useUdp = profile.useUdp,
            disableDtls = profile.disableDtls,
        )
    }

    fun dismissEditor() {
        editorState.value = null
    }

    fun updateEditor(transform: (ProfileEditorState) -> ProfileEditorState) {
        editorState.value = editorState.value?.let(transform)
    }

    fun importWireGuardFile(fileName: String, content: String) {
        updateEditor { current ->
            current.copy(
                wireGuardConfig = content,
                wireGuardSource = WireGuardProfileSource.FileImport(fileName, content),
            )
        }
    }

    fun importWireGuardQr(content: String) {
        updateEditor { current ->
            current.copy(
                wireGuardConfig = content,
                wireGuardSource = WireGuardProfileSource.QrImport(content),
            )
        }
    }

    fun saveEditor() {
        val editor = editorState.value ?: return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val profile = ConnectionProfile(
                    id = editor.id,
                    name = editor.name.trim(),
                    wireGuardSource = when (editor.wireGuardSource) {
                        is WireGuardProfileSource.FileImport ->
                            WireGuardProfileSource.FileImport(
                                (editor.wireGuardSource as WireGuardProfileSource.FileImport).fileName,
                                editor.wireGuardConfig,
                            )
                        is WireGuardProfileSource.QrImport -> WireGuardProfileSource.QrImport(editor.wireGuardConfig)
                        is WireGuardProfileSource.RawText -> WireGuardProfileSource.RawText(editor.wireGuardConfig)
                    },
                    wireGuardConfig = editor.wireGuardConfig.trim(),
                    callLink = editor.callLink.trim(),
                    relayHost = editor.relayHost.trim(),
                    relayPort = editor.relayPort.toInt(),
                    localProxyPort = editor.localProxyPort.toInt(),
                    mtu = editor.mtu.takeIf { it.isNotBlank() }?.toInt(),
                    dnsServers = editor.dnsServers.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) },
                    keepaliveSeconds = editor.keepaliveSeconds.takeIf { it.isNotBlank() }?.toInt(),
                    workerCount = editor.workerCount.toInt(),
                    useUdp = editor.useUdp,
                    disableDtls = editor.disableDtls,
                    createdAtMillis = if (editor.id == 0L) now else uiState.value.selectedProfile?.createdAtMillis ?: now,
                    updatedAtMillis = now,
                )
                val savedId = profileRepository.saveProfile(profile)
                settingsRepository.setSelectedProfileId(savedId)
                editorState.value = null
                transientMessage.value = "Profile saved"
            }.onFailure { throwable ->
                transientMessage.value = throwable.message ?: "Unable to save profile"
            }
        }
    }

    fun deleteSelectedProfile() {
        val profile = uiState.value.selectedProfile ?: return
        viewModelScope.launch {
            profileRepository.deleteProfile(profile.id)
            settingsRepository.setSelectedProfileId(profiles.value.firstOrNull { it.id != profile.id }?.id)
            transientMessage.value = "Profile deleted"
        }
    }

    fun getVpnPermissionIntent(): Intent? = wireGuardTunnelManager.getVpnPermissionIntent()

    fun toggleConnection() {
        val profile = uiState.value.selectedProfile ?: return
        viewModelScope.launch {
            when (uiState.value.connectionState) {
                is ConnectionState.Connected,
                is ConnectionState.StartingProxy,
                is ConnectionState.StartingVpn,
                is ConnectionState.Validating,
                is ConnectionState.Stopping,
                -> orchestrator.disconnect(profile.id)
                ConnectionState.Idle,
                is ConnectionState.Error,
                -> runCatching { orchestrator.connect(profile.id) }
                    .onFailure { throwable ->
                        transientMessage.value = throwable.message ?: "Unable to connect"
                    }
            }
        }
    }

    fun consumeTransientMessage() {
        transientMessage.value = null
    }
}
