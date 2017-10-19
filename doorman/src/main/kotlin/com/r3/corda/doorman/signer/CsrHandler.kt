package com.r3.corda.doorman.signer

import com.r3.corda.doorman.JiraClient
import com.r3.corda.doorman.buildCertPath
import com.r3.corda.doorman.persistence.CertificateResponse
import com.r3.corda.doorman.persistence.CertificationRequestStorage
import com.r3.corda.doorman.persistence.CertificationRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.doorman.persistence.RequestStatus
import org.bouncycastle.pkcs.PKCS10CertificationRequest

interface CsrHandler {
    fun saveRequest(rawRequest: PKCS10CertificationRequest): String
    fun processApprovedRequests()
    fun getResponse(requestId: String): CertificateResponse
}

class DefaultCsrHandler(private val storage: CertificationRequestStorage, private val signer: Signer?) : CsrHandler {
    override fun processApprovedRequests() {
        storage.getRequests(RequestStatus.Approved)
                .forEach { processRequest(it.requestId, PKCS10CertificationRequest(it.request)) }
    }

    private fun processRequest(requestId: String, request: PKCS10CertificationRequest) {
        if (signer != null) {
            val certs = signer.sign(request)
            // Since Doorman is deployed in the auto-signing mode (i.e. signer != null),
            // we use DOORMAN_SIGNATURE as the signer.
            storage.putCertificatePath(requestId, certs, listOf(DOORMAN_SIGNATURE))
        }
    }

    override fun saveRequest(rawRequest: PKCS10CertificationRequest): String {
        return storage.saveRequest(rawRequest)
    }

    override fun getResponse(requestId: String): CertificateResponse {
        val response = storage.getRequest(requestId)
        return when (response?.status) {
            RequestStatus.New, RequestStatus.Approved, null -> CertificateResponse.NotReady
            RequestStatus.Rejected -> CertificateResponse.Unauthorised(response.remark ?: "Unknown reason")
            RequestStatus.Signed -> CertificateResponse.Ready(buildCertPath(response.certificateData?.certificatePath ?: throw IllegalArgumentException("Certificate should not be null.")))
        }
    }
}

class JiraCsrHandler(private val jiraClient: JiraClient, private val storage: CertificationRequestStorage, private val delegate: CsrHandler) : CsrHandler by delegate {
    override fun saveRequest(rawRequest: PKCS10CertificationRequest): String {
        val requestId = delegate.saveRequest(rawRequest)
        // Make sure request has been accepted.
        if (delegate.getResponse(requestId) !is CertificateResponse.Unauthorised) {
            jiraClient.createRequestTicket(requestId, rawRequest)
        }
        return requestId
    }

    override fun processApprovedRequests() {
        jiraClient.getApprovedRequests().forEach { (id, approvedBy) -> storage.approveRequest(id, approvedBy) }
        delegate.processApprovedRequests()
        val signedRequests = storage.getRequests(RequestStatus.Signed).mapNotNull {
            it.certificateData?.certificatePath?.let { certs -> it.requestId to buildCertPath(certs) }
        }.toMap()
        jiraClient.updateSignedRequests(signedRequests)
    }
}
