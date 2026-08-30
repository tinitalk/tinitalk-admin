package org.tinitalk.admin.server

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

fun interface DnsLookup {
    suspend fun lookup(hostname: String): List<InetAddress>
}

class SystemDnsLookup : DnsLookup {
    override suspend fun lookup(hostname: String): List<InetAddress> = withContext(Dispatchers.IO) {
        runInterruptible { InetAddress.getAllByName(hostname).toList() }
    }
}

class DnsValidationException(message: String) : IllegalStateException(message)

class EndpointResolver(
    private val dnsLookup: DnsLookup = SystemDnsLookup(),
) {
    suspend fun resolve(enteredAddress: String, sshPort: Int): ResolvedEndpoint {
        val parsed = EndpointParser.parse(enteredAddress, sshPort)
        if (parsed.addressKind == AddressKind.IPV4) {
            return ResolvedEndpoint(
                parsed.enteredAddress,
                parsed.sshPort,
                parsed.enteredAddress,
                parsed.addressKind,
            )
        }

        return ResolvedEndpoint(
            enteredAddress = parsed.enteredAddress,
            sshPort = parsed.sshPort,
            frozenIpv4 = validatedSingleIpv4(parsed.enteredAddress),
            addressKind = parsed.addressKind,
        )
    }

    private suspend fun validatedSingleIpv4(hostname: String): String {
        val answers = try {
            withTimeout(DNS_TIMEOUT_MILLIS) { dnsLookup.lookup(hostname) }
        } catch (_: TimeoutCancellationException) {
            throw DnsValidationException("DNS lookup timed out")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw DnsValidationException("DNS lookup failed")
        }
        if (answers.any { it is Inet6Address }) {
            throw DnsValidationException("DNS name has an AAAA record")
        }
        val ipv4Answers = answers.filterIsInstance<Inet4Address>()
        if (ipv4Answers.size != answers.size) {
            throw DnsValidationException("DNS returned an unsupported address")
        }
        if (ipv4Answers.any { !EndpointParser.isGlobalPublicIpv4(it.address) }) {
            throw DnsValidationException("DNS returned a nonpublic IPv4 address")
        }
        val distinct = ipv4Answers.mapNotNull { it.hostAddress }.distinct()
        if (distinct.size != 1) {
            throw DnsValidationException("DNS name must resolve to one public IPv4 address")
        }
        return distinct.single()
    }

    private companion object {
        const val DNS_TIMEOUT_MILLIS = 10_000L
    }
}
