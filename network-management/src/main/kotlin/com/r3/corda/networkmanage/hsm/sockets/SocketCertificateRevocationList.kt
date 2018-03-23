package com.r3.corda.networkmanage.hsm.sockets

import com.r3.corda.networkmanage.common.persistence.CertificateRevocationListStorage
import com.r3.corda.networkmanage.common.persistence.CrlIssuer
import com.r3.corda.networkmanage.common.sockets.CertificateRevocationListSubmission
import com.r3.corda.networkmanage.common.sockets.CrlResponseMessage
import com.r3.corda.networkmanage.common.sockets.CrlRetrievalMessage
import com.r3.corda.networkmanage.common.sockets.CrlSubmissionMessage
import com.r3.corda.networkmanage.common.utils.readObject
import com.r3.corda.networkmanage.common.utils.writeObject
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import java.net.Socket
import java.security.cert.X509CRL
import java.time.Instant

class SocketCertificateRevocationList(private val serverHostAndPort: NetworkHostAndPort) : CertificateRevocationListStorage {

    private companion object {
        private val logger = contextLogger()
    }

    override fun getCertificateRevocationList(crlIssuer: CrlIssuer): X509CRL? {
        return Socket(serverHostAndPort.host, serverHostAndPort.port).use {
            logger.debug("Requesting the current revocation list...")
            it.getOutputStream().writeObject(CrlRetrievalMessage())
            it.getInputStream().readObject<CrlResponseMessage>().crl
        }
    }

    override fun saveCertificateRevocationList(crl: X509CRL, crlIssuer: CrlIssuer, signedBy: String, revokedAt: Instant) {
        Socket(serverHostAndPort.host, serverHostAndPort.port).use {
            it.getOutputStream().let {
                it.writeObject(CrlSubmissionMessage())
                logger.debug("Submitting a new revocation list...")
                it.writeObject(CertificateRevocationListSubmission(crl, signedBy, revokedAt))
            }
        }
    }
}