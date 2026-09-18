package org.tinitalk.admin.server

import kotlinx.coroutines.delay
import org.tinitalk.admin.ssh.SshCommandResult

class RemoteOperationStatusException(message: String) : Exception(message)

internal suspend fun readRemoteOperationStatus(
    kind: ServerOperationKind,
    query: suspend () -> SshCommandResult,
    onFailure: (RemoteOperationStatusException) -> Unit = {},
    waitBeforeRetry: suspend (Long) -> Unit = { delay(it) },
): RemoteServerOperation? {
    val retryDelays = longArrayOf(2_000L, 5_000L, 10_000L)
    var attempt = 0
    while (true) {
        try {
            return parseRemoteOperationStatus(kind, query())
        } catch (error: RemoteOperationStatusException) {
            onFailure(error)
            if (attempt == retryDelays.size) throw error
            waitBeforeRetry(retryDelays[attempt++])
        }
    }
}

private fun parseRemoteOperationStatus(
    kind: ServerOperationKind,
    result: SshCommandResult,
): RemoteServerOperation? {
    val values = result.stdout.lineSequence()
        .map(String::trim)
        .filter { it.contains('=') }
        .associate { it.substringBefore('=') to it.substringAfter('=') }

    // An unreadable response is not evidence that the service is absent.
    if (values["LoadState"] == "not-found" && values["ActiveState"] == "inactive") {
        return null
    }
    if (result.exitCode != 0 || values["LoadState"] != "loaded" ||
        values["ActiveState"].isNullOrBlank() || values["SubState"].isNullOrBlank()
    ) {
        throw RemoteOperationStatusException(
            "Cannot read ${kind.unitName}: exit=${result.exitCode}; " +
                "stdout=${result.stdout.take(2_048)}; stderr=${result.stderr.take(2_048)}",
        )
    }
    val state = when {
        values["ActiveState"] == "active" && values["SubState"] == "exited" ->
            RemoteOperationState.SUCCEEDED
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
    return RemoteServerOperation(kind, state, elapsedMillis, values["ExecMainStatus"]?.toIntOrNull())
}
