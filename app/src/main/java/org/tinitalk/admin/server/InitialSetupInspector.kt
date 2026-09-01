package org.tinitalk.admin.server

import android.content.Context
import org.tinitalk.admin.ssh.SshConnection

class InitialSetupInspector(context: Context) {
    private val script = context.applicationContext.assets
        .open(SCRIPT_NAME)
        .use { it.readBytes() }

    suspend fun inspect(
        connection: SshConnection,
        login: String,
        serverAddress: String,
        sshPort: Int,
    ): InitialSetupEvidence {
        val arguments = "-- ${serverAddress.shellQuote()} $sshPort"
        val command = if (login == "root") {
            "LC_ALL=C sh -s $arguments"
        } else {
            "LC_ALL=C sudo -n sh -s $arguments"
        }
        val result = connection.exec(command, script)
        check(result.exitCode == 0 && result.stderr.isBlank()) {
            "Initial setup status script failed"
        }
        val values = result.stdout.lineSequence()
            .filter(String::isNotEmpty)
            .associate { line ->
                val separator = line.indexOf('=')
                check(separator > 0) { "Invalid initial setup status output" }
                line.substring(0, separator) to line.substring(separator + 1)
            }
        check(values.keys == EXPECTED_FIELDS) { "Incomplete initial setup status output" }
        return InitialSetupEvidence(
            systemPackagesReady = values.yes("system_packages"),
            firewallReady = values.yes("firewall"),
            tlsCertificateReady = values.yes("tls_certificate"),
            tinitalkPrepared = values.yes("prepare_tinitalk"),
            binaryUploaded = values.yes("upload_binary"),
            tinitalkStarted = values.yes("start_tinitalk"),
            doctorReady = values.yes("doctor"),
            binaryInstalled = values.present("binary"),
            userPresent = values.present("user"),
            dataDirectoryPresent = values.present("data"),
            stateDatabasePresent = values.present("state"),
            tlsPresent = values.present("tls"),
            serviceInstalled = values.present("service"),
            serviceEnabled = values.yes("enabled"),
            serviceRunning = values.yes("running"),
        )
    }

    private fun Map<String, String>.present(key: String): Boolean = when (getValue(key)) {
        "present" -> true
        "missing" -> false
        else -> error("Invalid $key status")
    }

    private fun Map<String, String>.yes(key: String): Boolean = when (getValue(key)) {
        "yes" -> true
        "no" -> false
        else -> error("Invalid $key status")
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"

    private companion object {
        const val SCRIPT_NAME = "initial_setup_status.sh"
        val EXPECTED_FIELDS = setOf(
            "system_packages",
            "firewall",
            "tls_certificate",
            "prepare_tinitalk",
            "upload_binary",
            "start_tinitalk",
            "doctor",
            "binary",
            "user",
            "data",
            "state",
            "tls",
            "service",
            "enabled",
            "running",
        )
    }
}
