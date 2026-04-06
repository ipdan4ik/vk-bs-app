package com.lumus.vkapp.transport

import android.content.Context
import android.os.Build
import java.io.File

internal object VkTurnExecutableLoader {
    private const val NATIVE_LIB_NAME = "libvkturn.so"

    fun stageForCurrentAbi(context: Context): StagedBinary {
        require(Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            "Current device ABI is unsupported. arm64-v8a is required."
        }
        val file = File(context.applicationInfo.nativeLibraryDir, NATIVE_LIB_NAME)
        check(file.exists()) { "vkturn native binary not found at ${file.absolutePath}" }
        return StagedBinary(execPath = file.absolutePath)
    }

    fun close(stagedBinary: StagedBinary?) = Unit

    data class StagedBinary(val execPath: String)
}
