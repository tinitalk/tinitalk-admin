package org.tinitalk.admin.server

import android.content.Context
import org.tinitalk.admin.ssh.SshConnection

enum class TiniTalkServiceState {
    RUNNING,
    STOPPED,
    MISSING,
}

data class TiniTalkStatus(
    val os: String,
    val architecture: String,
    val binaryInstalled: Boolean,
    val service: TiniTalkServiceState,
    val dataDirectoryPresent: Boolean,
)

class TiniTalkStatusChecker(context: Context) {
    private val script = context.applicationContext.assets
        .open(SCRIPT_NAME)
        .use { it.readBytes() }

    suspend fun check(connection: SshConnection): TiniTalkStatus {
        val result = connection.exec("LC_ALL=C sh -s", script)
        check(result.exitCode == 0 && result.stderr.isBlank()) {
            "TiniTalk status script failed"
        }
        return parse(result.stdout)
    }

    private fun parse(output: String): TiniTalkStatus {
        val values = mutableMapOf<String, String>()
        output.lineSequence().filter(String::isNotEmpty).forEach { line ->
            val separator = line.indexOf('=')
            check(separator > 0) { "Invalid TiniTalk status output" }
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            check(values.put(key, value) == null) { "Duplicate TiniTalk status field" }
        }
        check(values.keys == EXPECTED_FIELDS) { "Incomplete TiniTalk status output" }

        val os = values.getValue("os").checkedText(128)
        val architecture = values.getValue("architecture").checkedText(32)
        val binaryInstalled = when (values.getValue("binary")) {
            "installed" -> true
            "missing" -> false
            else -> error("Invalid TiniTalk binary status")
        }
        val service = when (values.getValue("service")) {
            "running" -> TiniTalkServiceState.RUNNING
            "stopped" -> TiniTalkServiceState.STOPPED
            "missing" -> TiniTalkServiceState.MISSING
            else -> error("Invalid TiniTalk service status")
        }
        val dataDirectoryPresent = when (values.getValue("data")) {
            "present" -> true
            "missing" -> false
            else -> error("Invalid TiniTalk data status")
        }
        return TiniTalkStatus(os, architecture, binaryInstalled, service, dataDirectoryPresent)
    }

    private fun String.checkedText(maxLength: Int): String = also {
        check(isNotBlank() && length <= maxLength && none(Char::isISOControl)) {
            "Invalid TiniTalk status text"
        }
    }

    private companion object {
        const val SCRIPT_NAME = "tinitalk_status.sh"
        val EXPECTED_FIELDS = setOf("os", "architecture", "binary", "service", "data")
    }
}
