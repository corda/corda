package com.r3.corda.networkmanage.hsm.sockets

import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestData
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.common.sockets.CrrsByStatusMessage
import com.r3.corda.networkmanage.common.utils.readObject
import com.r3.corda.networkmanage.common.utils.writeObject
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import java.net.Socket

class CertificateRevocationRequestRetriever(private val serverHostAndPort: NetworkHostAndPort) {

    private companion object {
        private val logger = contextLogger()
    }

    fun retrieveApprovedCertificateRevocationRequests(): List<CertificateRevocationRequestData> {
        return retrieveCertificateRevocationRequests(RequestStatus.APPROVED)
    }

    fun retrieveDoneCertificateRevocationRequests(): List<CertificateRevocationRequestData> {
        return retrieveCertificateRevocationRequests(RequestStatus.DONE)
    }

    private fun retrieveCertificateRevocationRequests(status: RequestStatus): List<CertificateRevocationRequestData> {
        require(status == RequestStatus.DONE || status == RequestStatus.APPROVED) { "Allowed status values: APPROVED, DONE" }
        return Socket(serverHostAndPort.host, serverHostAndPort.port).use {
            it.getOutputStream().let {
                logger.debug("Requesting $status certificate revocation requests...")
                it.writeObject(CrrsByStatusMessage(status))
            }
            it.getInputStream().readObject()
        }
    }
}