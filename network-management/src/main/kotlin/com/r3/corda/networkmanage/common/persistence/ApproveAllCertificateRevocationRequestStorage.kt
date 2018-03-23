package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestStorage.Companion.DOORMAN_SIGNATURE
import net.corda.nodeapi.internal.network.CertificateRevocationRequest

/**
 * This storage automatically approves all created requests.
 */
class ApproveAllCertificateRevocationRequestStorage(private val delegate: CertificateRevocationRequestStorage) : CertificateRevocationRequestStorage by delegate {

    override fun saveRevocationRequest(request: CertificateRevocationRequest): String {
        val requestId = delegate.saveRevocationRequest(request)
        delegate.markRequestTicketCreated(requestId)
        approveRevocationRequest(requestId, DOORMAN_SIGNATURE)
        return requestId
    }
}
