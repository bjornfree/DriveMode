package com.bjornfree.drivemode.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class LogcatWatcher {

    private var process: Process? = null

    @Volatile
    var hasRootAccess: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun linesFlow(): Flow<String> = channelFlow {
        val cmdSu = arrayOf("su", "-c", "logcat -v time -T 1 -b main -b system")
        val cmdNormal = arrayOf("logcat", "-v", "time", "-T", "1", "-b", "main", "-b", "system")

        val proc = try {
            hasRootAccess = true
            lastError = null
            Runtime.getRuntime().exec(cmdSu)
        } catch (e: Exception) {
            hasRootAccess = false
            lastError = "Root access failed: ${e.message}"
            DriveModeService.logConsole("LogcatWatcher: root access failed, fallback to non-root")
            try {
                Runtime.getRuntime().exec(cmdNormal)
            } catch (e2: Exception) {
                lastError = "Logcat failed: ${e2.message}"
                DriveModeService.logConsole("LogcatWatcher: CRITICAL - both su and non-root logcat failed!")
                throw e2
            }
        }

        process = proc

        val stdout = BufferedReader(InputStreamReader(proc.inputStream))
        val stderr = BufferedReader(InputStreamReader(proc.errorStream))

        val stderrJob = this@channelFlow.launch(Dispatchers.IO) {
            try {
                var line: String? = ""
                while (isActive && stderr.readLine().also { line = it } != null) {
                    Log.e("DM-LC-ERR", line!!)
                }
            } catch (e: Exception) {
            } finally {
                try { stderr.close() } catch (_: Exception) {}
            }
        }

        try {
            var line: String? = ""
            while (isActive && stdout.readLine().also { line = it } != null) {
                trySendBlocking(line!!)
            }
        } catch (e: InterruptedIOException) {
            Log.i("DM", "Logcat reader interrupted (shutdown)")
        } catch (e: CancellationException) {
        } catch (e: Exception) {
            Log.i("DM", "Logcat reader closed: ${e.message}")
        } finally {
            try { stdout.close() } catch (_: Exception) {}
            try { stderr.close() } catch (_: Exception) {}
            try { proc.destroy() } catch (_: Exception) {}
            try { proc.waitFor(200, TimeUnit.MILLISECONDS) } catch (_: Exception) {}

            process = null
            stderrJob.cancel()
            close()
        }

        awaitClose {
            try { proc.destroy() } catch (_: Exception) {}
            process = null
        }
    }

    fun stop() {
        try { process?.destroy() } catch (_: Exception) {}
        process = null
    }

    companion object {
        private val REGEX_REAL_VALUE = Regex("realValue\\s+(\\d+)")

        fun parseModeOrNull(line: String): String? {
            if (!line.contains("QSCarPropertyManager")) return null
            if (!line.contains("handleDriveModeChange")) return null
            if (!line.contains("realValue")) return null

            val match = REGEX_REAL_VALUE.find(line) ?: return null
            val value = match.groupValues.getOrNull(1) ?: return null

            return when {
                value.endsWith("201") -> "adaptive"
                value.endsWith("139") -> "sport"
                value.endsWith("138") -> "comfort"
                value.endsWith("137") -> "eco"
                else -> null
            }
        }
    }
}