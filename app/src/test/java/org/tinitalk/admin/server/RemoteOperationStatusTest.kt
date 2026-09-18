package org.tinitalk.admin.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.tinitalk.admin.ssh.SshCommandResult
import org.tinitalk.admin.ssh.SshFailure

class RemoteOperationStatusTest {
    private val kind = ServerOperationKind.INSTALL_SYSTEM_PACKAGES

    @Test
    fun retriesFailedAndIncompleteQueriesWithoutLosingRunningOperation() = runBlocking {
        val replies = ArrayDeque(listOf(
            SshCommandResult(1, "", "Failed to connect to bus"),
            SshCommandResult(0, "ServerUptimeMonotonicUSec=2000000\n", ""),
            status("activating", "start"),
        ))
        val waits = mutableListOf<Long>()
        val failures = mutableListOf<RemoteOperationStatusException>()
        val result = readRemoteOperationStatus(
            kind, { replies.removeFirst() }, failures::add, { waits.add(it) },
        )
        assertEquals(RemoteOperationState.RUNNING, result?.state)
        assertEquals(listOf(2_000L, 5_000L), waits)
        assertEquals(2, failures.size)
        assertTrue(failures.first().message.orEmpty().contains("Failed to connect to bus"))
        assertTrue(replies.isEmpty())
    }

    @Test
    fun stopsAfterThreeRetries() = runBlocking {
        var queries = 0
        val waits = mutableListOf<Long>()
        try {
            readRemoteOperationStatus(
                kind,
                query = { queries++; SshCommandResult(127, "", "systemctl unavailable") },
                waitBeforeRetry = { waits.add(it) },
            )
            fail("Expected status failure")
        } catch (error: RemoteOperationStatusException) {
            assertTrue(error.message.orEmpty().contains("exit=127"))
        }
        assertEquals(4, queries)
        assertEquals(listOf(2_000L, 5_000L, 10_000L), waits)
    }

    @Test
    fun recognizesExplicitMissingUnitEvenWithNonzeroExit() = runBlocking {
        val result = readRemoteOperationStatus(
            kind,
            query = { SshCommandResult(1, "LoadState=not-found\nActiveState=inactive\n", "") },
            waitBeforeRetry = { fail("Missing unit must not be retried") },
        )
        assertNull(result)
    }

    @Test
    fun returnsActualServiceFailureWithoutRetry() = runBlocking {
        val result = readRemoteOperationStatus(
            kind, { status("failed", "failed", 1) },
            waitBeforeRetry = { fail("A failed operation must not be retried") },
        )
        assertEquals(RemoteOperationState.FAILED, result?.state)
        assertEquals(1, result?.exitCode)
    }

    @Test
    fun recognizesCompletedOperationAfterTemporaryFailure() = runBlocking {
        var queries = 0
        val result = readRemoteOperationStatus(
            kind,
            query = {
                if (queries++ == 0) SshCommandResult(1, "", "Bus unavailable")
                else status("active", "exited")
            },
            waitBeforeRetry = {},
        )
        assertEquals(RemoteOperationState.SUCCEEDED, result?.state)
        assertEquals(1_000L, result?.elapsedMillis)
    }

    @Test
    fun doesNotSwallowSshFailuresOrCancellation() = runBlocking {
        for (expected in listOf(SshFailure.AuthenticationFailed(), kotlinx.coroutines.CancellationException())) {
            try {
                readRemoteOperationStatus(
                    kind, { throw expected },
                    waitBeforeRetry = { fail("Unexpected retry") },
                )
                fail("Expected original exception")
            } catch (actual: Exception) {
                assertSame(expected, actual)
            }
        }
    }

    private fun status(active: String, sub: String, exit: Int = 0) = SshCommandResult(
        0,
        "LoadState=loaded\nActiveState=$active\nSubState=$sub\nExecMainStatus=$exit\n" +
            "ExecMainStartTimestampMonotonic=1000000\nServerUptimeMonotonicUSec=2000000\n",
        "",
    )
}
