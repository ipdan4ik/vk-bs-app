@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lumus.vkapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lumus.vkapp.domain.ConnectionProfile
import com.lumus.vkapp.domain.ConnectionState
import com.lumus.vkapp.domain.WireGuardProfileSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumusApp(
    state: MainUiState,
    onSelectProfile: (Long) -> Unit,
    onCreateProfile: () -> Unit,
    onEditProfile: (ConnectionProfile) -> Unit,
    onDeleteProfile: () -> Unit,
    onDismissEditor: () -> Unit,
    onUpdateEditor: ((ProfileEditorState) -> ProfileEditorState) -> Unit,
    onSaveEditor: () -> Unit,
    onImportFile: () -> Unit,
    onImportQr: () -> Unit,
    onToggleConnection: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFEEF6ED), Color(0xFFFFF3E8), Color(0xFFE7F2F1)),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Lumus VK VPN", fontWeight = FontWeight.SemiBold)
                            Text(
                                "WireGuard + VK TURN orchestration",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onCreateProfile) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add profile")
                        }
                    },
                )
            },
        ) { padding ->
            if (state.profiles.isEmpty()) {
                Onboarding(
                    modifier = Modifier.padding(padding),
                    onCreateProfile = onCreateProfile,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        if (state.transientMessage != null) {
                            MessageCard(message = state.transientMessage)
                        }
                    }
                    item {
                        SessionCard(
                            profile = state.selectedProfile,
                            connectionState = state.connectionState,
                            onConnect = onToggleConnection,
                            onEdit = {
                                state.selectedProfile?.let(onEditProfile)
                            },
                            onDelete = onDeleteProfile,
                        )
                    }
                    item {
                        Text(
                            "Profiles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(state.profiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            selected = profile.id == state.selectedProfileId,
                            onClick = { onSelectProfile(profile.id) },
                        )
                    }
                    item {
                        LogsCard(lines = state.diagnostics)
                    }
                }
            }
        }

        if (state.editorState != null) {
            ProfileEditorDialog(
                state = state.editorState,
                onDismiss = onDismissEditor,
                onUpdate = onUpdateEditor,
                onSave = onSaveEditor,
                onImportFile = onImportFile,
                onImportQr = onImportQr,
            )
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAE6D8)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF6A2F00),
        )
    }
}

@Composable
private fun Onboarding(
    modifier: Modifier = Modifier,
    onCreateProfile: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.86f)),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("No profiles yet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Create a WireGuard profile, attach a VK or Yandex call link, and the app will start the local TURN proxy before bringing up the VPN tunnel.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onCreateProfile) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create profile")
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    profile: ConnectionProfile?,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102A2A)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Session", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                profile?.name ?: "Select a profile",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                connectionState.presentableText(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFE7F2F1),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onConnect, enabled = profile != null) {
                    val connected = connectionState is ConnectionState.Connected
                    Icon(
                        if (connected) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (connected) "Disconnect" else "Connect")
                }
                OutlinedButton(onClick = onEdit, enabled = profile != null) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
                OutlinedButton(onClick = onDelete, enabled = profile != null) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ConnectionProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(onClick = onClick, label = { Text(if (selected) "Selected" else "Use") })
            }
            Text("${profile.relayHost}:${profile.relayPort}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(profile.callLink, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LogsCard(lines: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (lines.isEmpty()) {
                Text("Proxy logs will appear here after launch.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.takeLast(18).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    state: ProfileEditorState,
    onDismiss: () -> Unit,
    onUpdate: ((ProfileEditorState) -> ProfileEditorState) -> Unit,
    onSave: () -> Unit,
    onImportFile: () -> Unit,
    onImportQr: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFF9FBF7),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (state.id == 0L) "Create profile" else "Edit profile",
                    style = MaterialTheme.typography.headlineSmall,
                )
                LabeledField("Profile name", state.name) {
                    onUpdate { current -> current.copy(name = it, wireGuardSource = current.wireGuardSource) }
                }
                LabeledField("Relay host", state.relayHost) {
                    onUpdate { current -> current.copy(relayHost = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField(
                        label = "Relay port",
                        value = state.relayPort,
                        modifier = Modifier.weight(1f),
                    ) {
                        onUpdate { current -> current.copy(relayPort = it) }
                    }
                    LabeledField(
                        label = "Local proxy port",
                        value = state.localProxyPort,
                        modifier = Modifier.weight(1f),
                    ) {
                        onUpdate { current -> current.copy(localProxyPort = it) }
                    }
                }
                LabeledField("VK/Yandex call link", state.callLink) {
                    onUpdate { current -> current.copy(callLink = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("MTU", state.mtu, modifier = Modifier.weight(1f)) {
                        onUpdate { current -> current.copy(mtu = it) }
                    }
                    LabeledField("Keepalive", state.keepaliveSeconds, modifier = Modifier.weight(1f)) {
                        onUpdate { current -> current.copy(keepaliveSeconds = it) }
                    }
                    LabeledField("Workers", state.workerCount, modifier = Modifier.weight(1f)) {
                        onUpdate { current -> current.copy(workerCount = it) }
                    }
                }
                LabeledField("DNS overrides", state.dnsServers, placeholder = "1.1.1.1, 8.8.8.8") {
                    onUpdate { current -> current.copy(dnsServers = it) }
                }
                HorizontalDivider()
                Text("WireGuard config", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onImportFile) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import file")
                    }
                    OutlinedButton(onClick = onImportQr) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan QR")
                    }
                }
                OutlinedTextField(
                    value = state.wireGuardConfig,
                    onValueChange = { value ->
                        onUpdate {
                            it.copy(
                                wireGuardConfig = value,
                                wireGuardSource = WireGuardProfileSource.RawText(value),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    minLines = 8,
                    label = { Text("wg-quick config") },
                )
                SettingsToggle(
                    label = "Use UDP mode",
                    checked = state.useUdp,
                    onCheckedChange = { onUpdate { current -> current.copy(useUdp = it) } },
                )
                SettingsToggle(
                    label = "Disable DTLS",
                    checked = state.disableDtls,
                    onCheckedChange = { onUpdate { current -> current.copy(disableDtls = it) } },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSave) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save profile")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(placeholder)
            }
        },
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun ConnectionState.presentableText(): String = when (this) {
    ConnectionState.Idle -> "Idle"
    is ConnectionState.Validating -> "Validating profile and VPN permission"
    is ConnectionState.StartingProxy -> "Starting local VK TURN proxy"
    is ConnectionState.StartingVpn -> "Proxy ready. Bringing up WireGuard"
    is ConnectionState.Connected -> "VPN active via $tunnelName"
    is ConnectionState.Stopping -> "Stopping session"
    is ConnectionState.Error -> message
}
