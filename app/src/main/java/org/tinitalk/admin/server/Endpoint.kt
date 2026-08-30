package org.tinitalk.admin.server

import java.net.IDN
import java.net.InetAddress
import java.security.MessageDigest

enum class AddressKind {
    IPV4,
    DNS,
}

data class ResolvedEndpoint(
    val enteredAddress: String,
    val sshPort: Int,
    val frozenIpv4: String,
    val addressKind: AddressKind,
)

data class ParsedEndpoint(
    val enteredAddress: String,
    val sshPort: Int,
    val addressKind: AddressKind,
)

class EndpointValidationException(message: String) : IllegalArgumentException(message)

object EndpointParser {
    private val ipv4Shape = Regex("^[0-9]{1,3}(?:\\.[0-9]{1,3}){3}$")
    private val numericAddressShape = Regex("^[0-9.]+$")
    private val dnsLabel = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    fun parse(enteredAddress: String, sshPort: Int): ParsedEndpoint {
        if (sshPort !in 1..65_535) {
            throw EndpointValidationException("SSH port must be between 1 and 65535")
        }
        val address = enteredAddress.trim()
        if (
            address.isEmpty() ||
            address.any(Char::isWhitespace) ||
            address.contains("://") ||
            address.any { it in "@/?#[]:" }
        ) {
            throw EndpointValidationException("Enter an IPv4 address or DNS name")
        }

        if (ipv4Shape.matches(address)) {
            val bytes = parseIpv4Bytes(address)
                ?: throw EndpointValidationException("Invalid IPv4 address")
            if (!isGlobalPublicIpv4(bytes)) {
                throw EndpointValidationException("IPv4 address is not globally public")
            }
            return ParsedEndpoint(canonicalIpv4(bytes), sshPort, AddressKind.IPV4)
        }
        if (numericAddressShape.matches(address)) {
            throw EndpointValidationException("Invalid IPv4 address")
        }

        val canonical = try {
            IDN.toASCII(address.lowercase(), IDN.USE_STD3_ASCII_RULES)
        } catch (_: IllegalArgumentException) {
            throw EndpointValidationException("Invalid DNS name")
        }
        val labels = canonical.split('.')
        if (
            canonical.length > 253 ||
            labels.size < 2 ||
            labels.any { !dnsLabel.matches(it) } ||
            labels.last().length < 2 ||
            labels.last().all(Char::isDigit)
        ) {
            throw EndpointValidationException("Invalid DNS name")
        }
        return ParsedEndpoint(canonical, sshPort, AddressKind.DNS)
    }

    internal fun parseIpv4Bytes(address: String): ByteArray? {
        if (!ipv4Shape.matches(address)) return null
        val octets = address.split('.').map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return octets.map(Int::toByte).toByteArray()
    }

    internal fun isGlobalPublicIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return false
        val value = bytes.fold(0L) { result, byte ->
            (result shl 8) or (byte.toInt() and 0xff).toLong()
        }
        return NONPUBLIC_CIDRS.none { (network, prefix) -> value.inCidr(network, prefix) }
    }

    private fun canonicalIpv4(bytes: ByteArray): String =
        bytes.joinToString(".") { (it.toInt() and 0xff).toString() }

    private fun Long.inCidr(network: Long, prefix: Int): Boolean {
        val mask = (0xffff_ffffL shl (32 - prefix)) and 0xffff_ffffL
        return this and mask == network and mask
    }

    private val NONPUBLIC_CIDRS = listOf(
        0x0000_0000L to 8,
        0x0a00_0000L to 8,
        0x6440_0000L to 10,
        0x7f00_0000L to 8,
        0xa9fe_0000L to 16,
        0xac10_0000L to 12,
        0xc000_0000L to 24,
        0xc000_0200L to 24,
        0xc0a8_0000L to 16,
        0xc612_0000L to 15,
        0xc633_6400L to 24,
        0xcb00_7100L to 24,
        0xe000_0000L to 4,
        0xf000_0000L to 4,
    )
}

class PeerAddressMismatchException : IllegalStateException("SSH peer address changed")

object PinnedPeerVerifier {
    fun requireMatch(frozenIpv4: String, peer: InetAddress) {
        val expected = EndpointParser.parseIpv4Bytes(frozenIpv4)
            ?: throw PeerAddressMismatchException()
        if (!MessageDigest.isEqual(expected, peer.address)) {
            throw PeerAddressMismatchException()
        }
    }
}
