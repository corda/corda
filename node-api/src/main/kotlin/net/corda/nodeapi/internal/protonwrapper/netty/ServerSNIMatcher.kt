package net.corda.nodeapi.internal.protonwrapper.netty

import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.x509
import sun.security.x509.X500Name
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SNIServerName
import javax.net.ssl.StandardConstants

class ServerSNIMatcher(private val keyStore: CertificateStore) : SNIMatcher(0) {

    var matchedAlias: String?  = null
        private set
    var matchedServerName: String? = null
        private set

    override fun matches(serverName: SNIServerName): Boolean {
        if (serverName.type == StandardConstants.SNI_HOST_NAME) {
            keyStore.aliases().forEach { alias ->
                val x500Name = keyStore[alias].x509.subjectDN as X500Name
                val cordaX500Name = CordaX500Name.build(x500Name.asX500Principal())
                // Convert the CordaX500Name into the expected host name and compare
                // E.g. O=Corda B, L=London, C=GB becomes 3c6dd991936308edb210555103ffc1bb.corda.net
                if ((serverName as SNIHostName).asciiName == x500toHostName(cordaX500Name)) {
                    matchedAlias = alias
                    matchedServerName = serverName.asciiName
                    return true
                }
            }
        }

        return false
    }
}
