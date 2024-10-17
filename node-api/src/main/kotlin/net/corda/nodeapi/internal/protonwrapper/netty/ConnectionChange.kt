package net.corda.nodeapi.internal.protonwrapper.netty

import java.net.InetSocketAddress
import java.security.cert.X509Certificate

data class ConnectionChange(val remoteAddress: InetSocketAddress, val remoteCert: X509Certificate?, val connected: Boolean, val connectionResult: ConnectionResult) {
    override fun toString(): String {
        return "ConnectionChange remoteAddress: $remoteAddress connected state: $connected cert subject: ${remoteCert?.getSubjectX500Principal()} result: ${connectionResult}"
    }
}
