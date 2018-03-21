package com.r3.corda.networkmanage.common.persistence

import org.bouncycastle.pkcs.PKCS10CertificationRequest

/**
 * This storage automatically approves all created requests.
 */
class ApproveAllCertificateSigningRequestStorage(private val delegate: CertificateSigningRequestStorage) : CertificateSigningRequestStorage by delegate {
    override fun saveRequest(request: PKCS10CertificationRequest): String {
        val requestId = delegate.saveRequest(request)
        delegate.markRequestTicketCreated(requestId)
        approveRequest(requestId, CertificateSigningRequestStorage.DOORMAN_SIGNATURE)
        return requestId
    }
}
