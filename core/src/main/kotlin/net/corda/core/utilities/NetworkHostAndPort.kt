package net.corda.core.utilities

import net.corda.core.serialization.CordaSerializable
import java.net.URI
import java.net.URISyntaxException

/**
 * Tuple of host and port. Use [NetworkHostAndPort.parse] on untrusted data.
 * @param host a hostname or IP address. IPv6 addresses must not be enclosed in square brackets.
 * @param port a valid port number.
 */
@CordaSerializable
data class NetworkHostAndPort(val host: String, val port: Int) {
    companion object {
        internal const val INVALID_PORT_FORMAT = "Invalid port: %s"
        internal const val UNPARSEABLE_ADDRESS_FORMAT = "Unparseable address: %s"
        internal const val MISSING_PORT_FORMAT = "Missing port: %s"
        private val bracketedHost = "\\[(.*)]".toRegex()

        /**
         * Parses a string of the form host:port into a [NetworkHostAndPort].
         * The host part may be a hostname or IP address. If it's an IPv6 address, it must be enclosed in square brackets.
         * Note this does not parse the toString of a resolved [java.net.InetSocketAddress], which is of a host/IP:port form.
         * @throws IllegalArgumentException if the port is missing, the string is garbage, or the NetworkHostAndPort constructor rejected the parsed parts.
         */
        @JvmStatic
        fun parse(str: String): NetworkHostAndPort {
            val uri = try {
                URI(null, str, null, null, null)
            } catch (ex: URISyntaxException) {
                throw IllegalArgumentException("Host and port syntax is invalid, expected host:port")
            }
            require(uri.host != null) { NetworkHostAndPort.UNPARSEABLE_ADDRESS_FORMAT.format(str) }
            require(uri.port != -1) { NetworkHostAndPort.MISSING_PORT_FORMAT.format(str) }
            return NetworkHostAndPort(bracketedHost.matchEntire(uri.host)?.groupValues?.get(1) ?: uri.host, uri.port)
        }
    }

    init {
        require(port in (0..0xffff)) { INVALID_PORT_FORMAT.format(port) }
    }

    override fun toString() = if (':' in host) "[$host]:$port" else "$host:$port"
}
