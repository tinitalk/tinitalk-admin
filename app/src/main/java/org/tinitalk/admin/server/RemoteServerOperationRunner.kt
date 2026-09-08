package org.tinitalk.admin.server

import android.content.Context
import org.tinitalk.admin.ssh.SshConnection
import kotlinx.coroutines.delay

enum class ServerOperationKind(
    val unitName: String,
    internal val scriptName: String,
    internal val timeout: String,
    internal val receivesStagingDirectory: Boolean = false,
) {
    INSTALL_SYSTEM_PACKAGES(
        unitName = "tinitalk-admin-install-system-packages.service",
        scriptName = "install_system_packages.sh",
        timeout = "15min",
    ),
    CONFIGURE_FIREWALL(
        unitName = "tinitalk-admin-configure-firewall.service",
        scriptName = "configure_firewall.sh",
        timeout = "2min",
    ),
    SETUP_FAIL2BAN(
        unitName = "tinitalk-admin-setup-fail2ban.service",
        scriptName = "setup_fail2ban.sh",
        timeout = "5min",
    ),
    OBTAIN_TLS_CERTIFICATE(
        unitName = "tinitalk-admin-obtain-tls-certificate.service",
        scriptName = "obtain_tls_certificate.sh",
        timeout = "15min",
    ),
    PREPARE_TINITALK(
        unitName = "tinitalk-admin-prepare-tinitalk.service",
        scriptName = "prepare_tinitalk.sh",
        timeout = "1min",
    ),
    INSTALL_TINITALK_BINARY(
        unitName = "tinitalk-admin-install-tinitalk-binary.service",
        scriptName = "install_tinitalk_binary.sh",
        timeout = "2min",
        receivesStagingDirectory = true,
    ),
    START_TINITALK(
        unitName = "tinitalk-admin-start-tinitalk.service",
        scriptName = "start_tinitalk.sh",
        timeout = "2min",
    ),
    UPDATE_TINITALK(
        unitName = "tinitalk-admin-update-tinitalk.service",
        scriptName = "update_tinitalk.sh",
        timeout = "30min",
        receivesStagingDirectory = true,
    ),
}

data class RemoteOperationUpload(
    val fileName: String,
    val bytes: ByteArray,
)

enum class RemoteOperationState {
    RUNNING,
    SUCCEEDED,
    FAILED,
}

data class RemoteServerOperation(
    val kind: ServerOperationKind,
    val state: RemoteOperationState,
    val elapsedMillis: Long,
    val exitCode: Int?,
)

class RemoteServerOperationRunner(context: Context) {
    private val assets = context.applicationContext.assets
    private val wrapper = assets.open(WRAPPER_SCRIPT).use { it.readBytes() }
    private val scripts = ServerOperationKind.entries.associateWith { kind ->
        assets.open(kind.scriptName).use { it.readBytes() }
    }

    suspend fun start(
        connection: SshConnection,
        login: String,
        kind: ServerOperationKind,
        arguments: List<String>,
        uploads: List<RemoteOperationUpload> = emptyList(),
    ): RemoteServerOperation {
        status(connection, login, kind)?.let { existing ->
            if (existing.state == RemoteOperationState.RUNNING) return existing
            acknowledge(connection, login, kind)
        }

        val staging = connection.exec(CREATE_STAGING_COMMAND)
        check(staging.exitCode == 0 && staging.stderr.isBlank()) {
            "Failed to create operation staging directory"
        }
        val directory = staging.stdout.trim()
        check(STAGING_DIRECTORY.matches(directory)) { "Unsafe operation staging directory" }

        try {
            val wrapperPath = "$directory/$WRAPPER_SCRIPT"
            val scriptPath = "$directory/${kind.scriptName}"
            connection.upload(wrapper, wrapperPath, SCRIPT_MODE)
            connection.upload(scripts.getValue(kind), scriptPath, SCRIPT_MODE)
            uploads.forEach { upload ->
                require(UPLOAD_FILE_NAME.matches(upload.fileName)) { "Unsafe upload file name" }
                connection.upload(
                    bytes = upload.bytes,
                    remotePath = "$directory/${upload.fileName}",
                    mode = UPLOAD_MODE,
                    timeoutMillis = UPLOAD_TIMEOUT_MILLIS,
                )
            }

            val command = buildString {
                append("systemd-run --no-block")
                append(" --unit=").append(kind.unitName)
                append(" --property=Type=oneshot")
                append(" --property=RemainAfterExit=yes")
                append(" --property=TimeoutStartSec=").append(kind.timeout)
                append(" --setenv=LC_ALL=C")
                append(" /bin/sh ").append(wrapperPath.shellQuote())
                append(' ').append(directory.shellQuote())
                append(' ').append(scriptPath.shellQuote())
                if (kind.receivesStagingDirectory) {
                    append(' ').append(directory.shellQuote())
                }
                arguments.forEach { append(' ').append(it.shellQuote()) }
            }
            val result = connection.exec(privileged(login, command))
            if (result.exitCode != 0) {
                status(connection, login, kind)?.let { existing ->
                    connection.exec("LC_ALL=C rm -rf -- ${directory.shellQuote()}")
                    return existing
                }
                error("Failed to start remote server operation")
            }

            repeat(START_STATUS_ATTEMPTS) {
                status(connection, login, kind)?.let { return it }
                delay(START_STATUS_DELAY_MILLIS)
            }
            error("Remote server operation did not appear")
        } catch (error: Throwable) {
            connection.exec("LC_ALL=C rm -rf -- ${directory.shellQuote()}")
            throw error
        }
    }

    suspend fun find(
        connection: SshConnection,
        login: String,
        kinds: Collection<ServerOperationKind> = ServerOperationKind.entries,
    ): RemoteServerOperation? {
        val found = kinds.mapNotNull { status(connection, login, it) }
        return found.firstOrNull { it.state == RemoteOperationState.RUNNING } ?: found.firstOrNull()
    }

    suspend fun status(
        connection: SshConnection,
        login: String,
        kind: ServerOperationKind,
    ): RemoteServerOperation? {
        val command = buildString {
            append("systemctl show ").append(kind.unitName)
            append(" --no-pager")
            append(" --property=LoadState")
            append(" --property=ActiveState")
            append(" --property=SubState")
            append(" --property=Result")
            append(" --property=ExecMainStatus")
            append(" --property=ExecMainStartTimestampMonotonic")
            append("; awk '{printf \"ServerUptimeMonotonicUSec=%.0f\\n\", \$1 * 1000000}' /proc/uptime")
        }
        val result = connection.exec(privileged(login, command))
        check(result.exitCode == 0) { "Failed to read remote operation status" }
        val values = result.stdout.lineSequence()
            .map(String::trim)
            .filter { it.contains('=') }
            .associate { line -> line.substringBefore('=') to line.substringAfter('=') }
        if (values["LoadState"] != "loaded") return null

        val state = when {
            values["ActiveState"] == "active" && values["SubState"] == "exited" -> {
                RemoteOperationState.SUCCEEDED
            }
            values["ActiveState"] == "failed" -> RemoteOperationState.FAILED
            else -> RemoteOperationState.RUNNING
        }
        val startedAt = values["ExecMainStartTimestampMonotonic"]?.toLongOrNull() ?: 0
        val serverUptime = values["ServerUptimeMonotonicUSec"]?.toLongOrNull() ?: startedAt
        val elapsedMillis = if (startedAt > 0 && serverUptime >= startedAt) {
            (serverUptime - startedAt) / 1_000
        } else {
            0
        }
        val exitCode = values["ExecMainStatus"]?.toIntOrNull()
        return RemoteServerOperation(kind, state, elapsedMillis, exitCode)
    }

    suspend fun acknowledge(
        connection: SshConnection,
        login: String,
        kind: ServerOperationKind,
    ) {
        val command = "systemctl stop ${kind.unitName} >/dev/null 2>&1 || true; " +
            "systemctl reset-failed ${kind.unitName} >/dev/null 2>&1 || true"
        connection.exec(privileged(login, command))
    }

    private fun privileged(login: String, command: String): String = if (login == "root") {
        "LC_ALL=C $command"
    } else {
        "LC_ALL=C sudo -n sh -c ${command.shellQuote()}"
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"

    private companion object {
        const val WRAPPER_SCRIPT = "run_server_operation.sh"
        const val CREATE_STAGING_COMMAND =
            "LC_ALL=C umask 077; mktemp -d /tmp/tinitalk-admin-operation.XXXXXXXXXX"
        const val SCRIPT_MODE = 448
        const val UPLOAD_MODE = 384
        const val UPLOAD_TIMEOUT_MILLIS = 5 * 60 * 1_000L
        const val START_STATUS_ATTEMPTS = 10
        const val START_STATUS_DELAY_MILLIS = 100L
        val STAGING_DIRECTORY = Regex("^/tmp/tinitalk-admin-operation\\.[A-Za-z0-9]+$")
        val UPLOAD_FILE_NAME = Regex("^[A-Za-z0-9._-]+$")
    }
}
