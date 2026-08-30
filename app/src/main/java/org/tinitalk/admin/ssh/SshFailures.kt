package org.tinitalk.admin.ssh

sealed class SshFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class HostKeyChanged : SshFailure("SSH host key changed")
    class AuthenticationFailed : SshFailure("SSH authentication failed")
    class Timeout : SshFailure("SSH operation timed out")
    class Transport(cause: Throwable) : SshFailure("SSH transport failed", cause)
}
