package net.corda.core.utilities

import java.net.URI

/**
 * Tuple of host and port. Use [parseNetworkHostAndPort] on untrusted data.
 * @param host a hostname or IP address. IPv6 addresses must not be enclosed in square brackets.
 * @param port a valid port number.
 */
data class NetworkHostAndPort(val host: String, val port: Int) {
    init {
        require(port in (0..0xffff)) { invalidPortFormat.format(port) }
    }

    override fun toString() = if (':' in host) "[$host]:$port" else "$host:$port"
}

/**
 * Parses a string of the form host:port into a [NetworkHostAndPort].
 * The host part may be a hostname or IP address. If it's an IPv6 address, it must be enclosed in square brackets.
 * Note this does not parse the toString of a resolved [java.net.InetSocketAddress], which is of a host/IP:port form.
 * @throws IllegalArgumentException if the port is missing, the string is garbage, or the NetworkHostAndPort constructor rejected the parsed parts.
 */
fun String.parseNetworkHostAndPort() = run {
    val uri = URI(null, this, null, null, null)
    require(uri.host != null) { unparseableAddressFormat.format(this) }
    require(uri.port != -1) { missingPortFormat.format(this) }
    NetworkHostAndPort(bracketedHost.matchEntire(uri.host)?.groupValues?.get(1) ?: uri.host, uri.port)
}

private val bracketedHost = "\\[(.*)]".toRegex()
internal val invalidPortFormat = "Invalid port: %s"
internal val unparseableAddressFormat = "Unparseable address: %s"
internal val missingPortFormat = "Missing port: %s"
