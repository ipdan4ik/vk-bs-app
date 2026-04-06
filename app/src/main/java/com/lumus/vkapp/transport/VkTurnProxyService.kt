package com.lumus.vkapp.transport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.lumus.vkapp.MainActivity
import com.lumus.vkapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class VkTurnProxyService : Service() {
    companion object {
        const val ACTION_START = "com.lumus.vkapp.action.START_PROXY"
        const val ACTION_STOP = "com.lumus.vkapp.action.STOP_PROXY"

        const val EXTRA_RELAY_PEER = "relay_peer"
        const val EXTRA_CALL_LINK = "call_link"
        const val EXTRA_CALL_PROVIDER = "call_provider"
        const val EXTRA_LOCAL_HOST = "local_host"
        const val EXTRA_LOCAL_PORT = "local_port"
        const val EXTRA_WORKERS = "workers"
        const val EXTRA_USE_UDP = "use_udp"
        const val EXTRA_DISABLE_DTLS = "disable_dtls"

        private const val CHANNEL_ID = "vk_turn_proxy"
        private const val NOTIFICATION_ID = 2001
        private const val LOG_LIMIT = 250

        private val _state = MutableStateFlow<ProxyRuntimeState>(ProxyRuntimeState.Idle)
        private val _logs = MutableStateFlow(emptyList<String>())

        val state = _state.asStateFlow()
        val logs = _logs.asStateFlow()

        internal fun addLog(message: String) {
            val next = (_logs.value + message).takeLast(LOG_LIMIT)
            _logs.value = next
        }

        internal fun setState(newState: ProxyRuntimeState) {
            _state.value = newState
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var waitJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopRequested = false
    private var readyEmitted = false
    private var foregroundActive = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ensureForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            ensureForeground()
            startProxy(intent)
        } else if (intent?.action == ACTION_STOP) {
            stopProxy()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRequested = true
        waitJob?.cancel()
        process?.destroy()
        wakeLock?.takeIf { it.isHeld }?.release()
        foregroundActive = false
        if (state.value !is ProxyRuntimeState.Failed) {
            setState(ProxyRuntimeState.Idle)
        }
        super.onDestroy()
    }

    private fun startProxy(intent: Intent) {
        if (state.value == ProxyRuntimeState.Ready || state.value == ProxyRuntimeState.Starting) {
            return
        }
        if (!Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            setState(ProxyRuntimeState.Failed("Current device ABI is unsupported. arm64-v8a is required."))
            return
        }

        stopRequested = false
        readyEmitted = false
        _logs.value = emptyList()
        addLog("=== STARTING VK TURN PROXY ===")
        setState(ProxyRuntimeState.Starting)
        acquireWakeLock()

        val config = ProxySessionConfig(
            relayPeer = intent.getStringExtra(EXTRA_RELAY_PEER).orEmpty(),
            callLink = intent.getStringExtra(EXTRA_CALL_LINK).orEmpty(),
            callProvider = CallProvider.valueOf(intent.getStringExtra(EXTRA_CALL_PROVIDER) ?: CallProvider.VK.name),
            localListenHost = intent.getStringExtra(EXTRA_LOCAL_HOST) ?: "127.0.0.1",
            localListenPort = intent.getIntExtra(EXTRA_LOCAL_PORT, 9000),
            workerCount = intent.getIntExtra(EXTRA_WORKERS, 8),
            useUdp = intent.getBooleanExtra(EXTRA_USE_UDP, true),
            disableDtls = intent.getBooleanExtra(EXTRA_DISABLE_DTLS, false),
        )
        serviceScope.launch {
            var stagedBinary: VkTurnExecutableLoader.StagedBinary? = null
            runCatching {
                stagedBinary = VkTurnExecutableLoader.stageForCurrentAbi(this@VkTurnProxyService)
                addLog("Binary: ${stagedBinary!!.execPath}")
                val command = buildCommand(stagedBinary!!.execPath, config)
                addLog("Command: ${command.joinToString(" ")}")
                process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                VkTurnExecutableLoader.close(stagedBinary)
                stagedBinary = null
                addLog("Process spawned")

                serviceScope.launch {
                    delay(1200)
                    val alive = process?.isAlive == true
                    addLog("Alive check at 1200ms: $alive")
                    if (!stopRequested && alive && !readyEmitted) {
                        readyEmitted = true
                        setState(ProxyRuntimeState.Ready)
                        addLog("Proxy assumed ready")
                    } else if (!stopRequested && !alive && !readyEmitted) {
                        addLog("Process already dead at 1200ms — waiting for exit code")
                    }
                }

                try {
                    BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            addLog(line)
                            if (!readyEmitted && line.containsReadySignal()) {
                                readyEmitted = true
                                setState(ProxyRuntimeState.Ready)
                            }
                        }
                    }
                } catch (e: java.io.IOException) {
                    if (!stopRequested) throw e
                }

                val stillAlive = process?.isAlive == true
                addLog("Stream closed. Process alive: $stillAlive")
                if (stillAlive) {
                    if (!stopRequested && !readyEmitted) {
                        readyEmitted = true
                        setState(ProxyRuntimeState.Ready)
                        addLog("Process detached stdout, assumed ready")
                    }
                    waitJob = serviceScope.launch {
                        val exitCode = try {
                            process?.waitFor() ?: -1
                        } catch (e: Exception) {
                            addLog("waitFor threw: ${e.javaClass.simpleName}: ${e.message}")
                            -1
                        }
                        addLog("Process exited with code $exitCode")
                        if (!stopRequested) {
                            setState(ProxyRuntimeState.Failed("vkturn exited with code $exitCode"))
                            stopSelf()
                        }
                    }
                } else {
                    val exitCode = try { process?.exitValue() ?: -1 } catch (e: Exception) { -1 }
                    addLog("Process exited with code $exitCode")
                    if (!stopRequested) {
                        setState(ProxyRuntimeState.Failed("vkturn exited with code $exitCode"))
                        stopSelf()
                    }
                }
            }.getOrElse { throwable ->
                VkTurnExecutableLoader.close(stagedBinary)
                if (!stopRequested) {
                    addLog("Critical failure: ${throwable.javaClass.simpleName}: ${throwable.message}")
                    setState(ProxyRuntimeState.Failed(throwable.message ?: "Unknown proxy error"))
                    stopSelf()
                }
            }
        }
    }

    private fun stopProxy() {
        stopRequested = true
        setState(ProxyRuntimeState.Stopping)
        addLog("=== STOPPING PROXY ===")
        waitJob?.cancel()
        process?.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundActive = false
        stopSelf()
    }

    private fun buildCommand(binaryPath: String, config: ProxySessionConfig): List<String> {
        val command = mutableListOf(
            binaryPath,
            "-peer",
            config.relayPeer,
            if (config.callProvider == CallProvider.YANDEX) "-yandex-link" else "-vk-link",
            config.callLink,
            "-listen",
            "${config.localListenHost}:${config.localListenPort}",
            "-n",
            config.workerCount.toString(),
        )
        if (config.useUdp) {
            command += "-udp"
        }
        if (config.disableDtls) {
            command += "-no-dtls"
        }
        return command
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text_running))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .build()
    }

    private fun ensureForeground() {
        if (foregroundActive) {
            return
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundActive = true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (wakeLock?.isHeld == true) {
            return
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LumusVkApp:ProxyWakeLock").apply {
            acquire()
        }
    }
}

private fun String.containsReadySignal(): Boolean {
    val lowered = lowercase()
    return lowered.contains("listen") ||
        lowered.contains("ready") ||
        lowered.contains("started") ||
        lowered.contains("proxying")
}
