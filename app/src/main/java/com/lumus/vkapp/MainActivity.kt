package com.lumus.vkapp

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.lumus.vkapp.ui.LumusApp
import com.lumus.vkapp.ui.MainViewModel
import com.lumus.vkapp.ui.theme.LumusTheme
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LumusTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val qrOptions = remember {
                    ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan a WireGuard QR config")
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                        captureActivity = com.journeyapps.barcodescanner.CaptureActivity::class.java
                        addExtra(Intents.Scan.CAMERA_ID, 0)
                    }
                }

                val filePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        val content = contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream)).readText()
                        }.orEmpty()
                        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "wg.conf"
                        viewModel.importWireGuardFile(fileName, content)
                    }
                }

                val qrLauncher = rememberLauncherForActivityResult(
                    contract = ScanContract(),
                ) { result ->
                    result.contents?.takeIf { it.isNotBlank() }?.let(viewModel::importWireGuardQr)
                }

                val vpnPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    if (viewModel.getVpnPermissionIntent() == null) {
                        viewModel.toggleConnection()
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) {
                    if (it) {
                        val intent = viewModel.getVpnPermissionIntent()
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            viewModel.toggleConnection()
                        }
                    }
                }

                LumusApp(
                    state = uiState,
                    onSelectProfile = viewModel::selectProfile,
                    onCreateProfile = viewModel::showCreateProfile,
                    onEditProfile = { profile -> viewModel.editProfile(profile) },
                    onDeleteProfile = viewModel::deleteSelectedProfile,
                    onDismissEditor = viewModel::dismissEditor,
                    onUpdateEditor = viewModel::updateEditor,
                    onSaveEditor = viewModel::saveEditor,
                    onImportFile = { filePicker.launch(arrayOf("*/*")) },
                    onImportQr = { qrLauncher.launch(qrOptions) },
                    onToggleConnection = {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val intent = viewModel.getVpnPermissionIntent()
                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            } else {
                                viewModel.toggleConnection()
                            }
                        }
                    },
                )
            }
        }
    }
}
