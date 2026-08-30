package org.tinitalk.admin.server

import org.tinitalk.admin.ssh.SshConnection

data class ServerUser(
    val login: String,
    val displayName: String,
    val disabled: Boolean,
)

data class AddedServerUser(
    val user: ServerUser,
    val token: String,
)

class ServerUserAlreadyExistsException : IllegalStateException()
class ServerUserAdministrativeAccessException : IllegalStateException()
class ServerUserCommandUnavailableException : IllegalStateException()
class ServerUserStorageException : IllegalStateException()
class ServerUserNotFoundException : IllegalStateException()

class ServerUsersReader {
    suspend fun read(connection: SshConnection, login: String): List<ServerUser> {
        val command = if (login == "root") {
            "LC_ALL=C /usr/local/bin/tinitalk user list --data-dir /var/lib/tinitalk"
        } else {
            "LC_ALL=C sudo -n /usr/local/bin/tinitalk user list --data-dir /var/lib/tinitalk"
        }
        val result = connection.exec(command)
        check(result.exitCode == 0 && result.stderr.isBlank()) {
            "TiniTalk user list command failed"
        }
        return result.stdout.lineSequence()
            .filter(String::isNotEmpty)
            .map(::parseUser)
            .toList()
    }

    suspend fun add(
        connection: SshConnection,
        sshLogin: String,
        login: String,
        displayName: String,
    ): AddedServerUser {
        val executable = if (sshLogin == "root") {
            "/usr/local/bin/tinitalk"
        } else {
            "sudo -n /usr/local/bin/tinitalk"
        }
        val command = buildString {
            append("LC_ALL=C ").append(executable)
            append(" user add --data-dir /var/lib/tinitalk ")
            append(login.shellQuote()).append(' ')
            append(displayName.shellQuote())
        }
        val result = connection.exec(command)
        if (result.exitCode != 0) {
            val error = result.stderr.lowercase()
            when {
                "unique constraint failed: users.login" in error ->
                    throw ServerUserAlreadyExistsException()
                "sudo:" in error -> throw ServerUserAdministrativeAccessException()
                "tinitalk: not found" in error ||
                    "/usr/local/bin/tinitalk: no such file" in error ->
                    throw ServerUserCommandUnavailableException()
                "database is locked" in error ||
                    "unable to open database file" in error ||
                    "permission denied" in error -> throw ServerUserStorageException()
            }
        }
        check(result.exitCode == 0 && result.stderr.isBlank()) {
            "TiniTalk user add command failed"
        }
        val token = parseTokenOutput(result.stdout, login)
        return AddedServerUser(
            user = ServerUser(login = login, displayName = displayName, disabled = false),
            token = token,
        )
    }

    suspend fun rotateToken(
        connection: SshConnection,
        sshLogin: String,
        login: String,
    ): String {
        val executable = if (sshLogin == "root") {
            "/usr/local/bin/tinitalk"
        } else {
            "sudo -n /usr/local/bin/tinitalk"
        }
        val command = buildString {
            append("LC_ALL=C ").append(executable)
            append(" user rotate-token --data-dir /var/lib/tinitalk ")
            append(login.shellQuote())
        }
        val result = connection.exec(command)
        if (result.exitCode != 0) {
            val error = result.stderr.lowercase()
            when {
                "no rows in result set" in error -> throw ServerUserNotFoundException()
                "sudo:" in error -> throw ServerUserAdministrativeAccessException()
                "tinitalk: not found" in error ||
                    "/usr/local/bin/tinitalk: no such file" in error ->
                    throw ServerUserCommandUnavailableException()
                "database is locked" in error ||
                    "unable to open database file" in error ||
                    "permission denied" in error -> throw ServerUserStorageException()
            }
        }
        check(result.exitCode == 0 && result.stderr.isBlank()) {
            "TiniTalk user rotate-token command failed"
        }
        return parseTokenOutput(result.stdout, login)
    }

    suspend fun delete(connection: SshConnection, sshLogin: String, login: String) {
        val executable = if (sshLogin == "root") {
            "/usr/local/bin/tinitalk"
        } else {
            "sudo -n /usr/local/bin/tinitalk"
        }
        val command = buildString {
            append("LC_ALL=C ").append(executable)
            append(" user delete --data-dir /var/lib/tinitalk ")
            append(login.shellQuote())
        }
        val result = connection.exec(command)
        if (result.exitCode != 0) {
            val error = result.stderr.lowercase()
            when {
                "user not found" in error -> throw ServerUserNotFoundException()
                "sudo:" in error -> throw ServerUserAdministrativeAccessException()
                "tinitalk: not found" in error ||
                    "/usr/local/bin/tinitalk: no such file" in error ->
                    throw ServerUserCommandUnavailableException()
                "database is locked" in error ||
                    "unable to open database file" in error ||
                    "permission denied" in error -> throw ServerUserStorageException()
            }
        }
        check(
            result.exitCode == 0 &&
                result.stderr.isBlank() &&
                result.stdout == "deleted: $login\n",
        ) {
            "TiniTalk user delete command failed"
        }
    }

    suspend fun setDisabled(
        connection: SshConnection,
        sshLogin: String,
        login: String,
        disabled: Boolean,
    ) {
        val executable = if (sshLogin == "root") {
            "/usr/local/bin/tinitalk"
        } else {
            "sudo -n /usr/local/bin/tinitalk"
        }
        val action = if (disabled) "disable" else "enable"
        val command = buildString {
            append("LC_ALL=C ").append(executable).append(" user ").append(action)
            append(" --data-dir /var/lib/tinitalk ").append(login.shellQuote())
        }
        val result = connection.exec(command)
        if (result.exitCode != 0) {
            val error = result.stderr.lowercase()
            when {
                "user not found" in error -> throw ServerUserNotFoundException()
                "sudo:" in error -> throw ServerUserAdministrativeAccessException()
                "tinitalk: not found" in error ||
                    "/usr/local/bin/tinitalk: no such file" in error ->
                    throw ServerUserCommandUnavailableException()
                "database is locked" in error ||
                    "unable to open database file" in error ||
                    "permission denied" in error -> throw ServerUserStorageException()
            }
        }
        val expectedOutput = "${if (disabled) "disabled" else "enabled"}: $login\n"
        check(
            result.exitCode == 0 &&
                result.stderr.isBlank() &&
                result.stdout == expectedOutput,
        ) {
            "TiniTalk user $action command failed"
        }
    }

    suspend fun rename(
        connection: SshConnection,
        sshLogin: String,
        login: String,
        displayName: String,
    ) {
        val executable = if (sshLogin == "root") {
            "/usr/local/bin/tinitalk"
        } else {
            "sudo -n /usr/local/bin/tinitalk"
        }
        val command = buildString {
            append("LC_ALL=C ").append(executable)
            append(" user rename --data-dir /var/lib/tinitalk ")
            append(login.shellQuote()).append(' ').append(displayName.shellQuote())
        }
        val result = connection.exec(command)
        if (result.exitCode != 0) {
            val error = result.stderr.lowercase()
            when {
                "user not found" in error -> throw ServerUserNotFoundException()
                "sudo:" in error -> throw ServerUserAdministrativeAccessException()
                "tinitalk: not found" in error ||
                    "/usr/local/bin/tinitalk: no such file" in error ->
                    throw ServerUserCommandUnavailableException()
                "database is locked" in error ||
                    "unable to open database file" in error ||
                    "permission denied" in error -> throw ServerUserStorageException()
            }
        }
        check(
            result.exitCode == 0 &&
                result.stderr.isBlank() &&
                result.stdout == "renamed: $login\n",
        ) {
            "TiniTalk user rename command failed"
        }
    }

    private fun parseUser(line: String): ServerUser {
        val firstSeparator = line.indexOf('\t')
        val lastSeparator = line.lastIndexOf('\t')
        check(firstSeparator > 0 && lastSeparator > firstSeparator) {
            "Invalid TiniTalk user list output"
        }
        val status = line.substring(lastSeparator + 1)
        check(status == "enabled" || status == "disabled") {
            "Invalid TiniTalk user status"
        }
        return ServerUser(
            login = line.substring(0, firstSeparator).also(::checkText),
            displayName = line.substring(firstSeparator + 1, lastSeparator).also(::checkText),
            disabled = status == "disabled",
        )
    }

    private fun checkText(value: String) {
        check(value.isNotBlank() && value.none { it == '\r' || it == '\n' }) {
            "Invalid TiniTalk user text"
        }
    }

    private fun parseTokenOutput(output: String, login: String): String {
        val lines = output.lineSequence().filter(String::isNotEmpty).toList()
        check(lines.size == 2 && lines[0] == "login: $login" && lines[1].startsWith("token: ")) {
            "Invalid TiniTalk user token output"
        }
        return lines[1].removePrefix("token: ").also { token ->
            check(token.length == TOKEN_LENGTH && token.all(::isTokenCharacter)) {
                "Invalid TiniTalk user token"
            }
        }
    }

    private fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"

    private fun isTokenCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == '-' || character == '_'

    private companion object {
        const val TOKEN_LENGTH = 43
    }
}
