package com.r3.corda.networkmanage.doorman.signer

import com.r3.corda.networkmanage.common.persistence.CertificateResponse
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequest
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage.Companion.DOORMAN_SIGNATURE
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.doorman.JiraClient
import net.corda.core.utilities.loggerFor
import org.bouncycastle.pkcs.PKCS10CertificationRequest

interface CsrHandler {
    fun saveRequest(rawRequest: PKCS10CertificationRequest): String
    fun createTickets()
    fun processApprovedRequests()
    fun getResponse(requestId: String): CertificateResponse
}

class DefaultCsrHandler(private val storage: CertificationRequestStorage, private val signer: LocalSigner?) : CsrHandler {
    override fun processApprovedRequests() {
        storage.getRequests(RequestStatus.APPROVED)
                .forEach { processRequest(it.requestId, it.request) }
    }

    override fun createTickets() { }

    private fun processRequest(requestId: String, request: PKCS10CertificationRequest) {
        if (signer != null) {
            val certs = signer.createSignedClientCertificate(request)
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
            RequestStatus.NEW, RequestStatus.APPROVED, RequestStatus.TICKET_CREATED, null -> CertificateResponse.NotReady
            RequestStatus.REJECTED -> CertificateResponse.Unauthorised(response.remark ?: "Unknown reason")
            RequestStatus.SIGNED -> CertificateResponse.Ready(response.certData?.certPath ?: throw IllegalArgumentException("Certificate should not be null."))
        }
    }
}

class JiraCsrHandler(private val jiraClient: JiraClient, private val storage: CertificationRequestStorage, private val delegate: CsrHandler) : CsrHandler by delegate {
    private companion object {
        val log = loggerFor<JiraCsrHandler>()
    }

    override fun saveRequest(rawRequest: PKCS10CertificationRequest): String {
        val requestId = delegate.saveRequest(rawRequest)
        // Make sure request has been accepted.
        try {
            if (delegate.getResponse(requestId) !is CertificateResponse.Unauthorised) {
                jiraClient.createRequestTicket(requestId, rawRequest)
                storage.markRequestTicketCreated(requestId)
            }
        } catch (e: Exception) {
            log.warn("There was an error while creating Jira tickets", e)
        } finally {
            return requestId
        }
    }

    override fun processApprovedRequests() {
        jiraClient.getApprovedRequests().forEach { (id, approvedBy) -> storage.approveRequest(id, approvedBy) }
        delegate.processApprovedRequests()
        val signedRequests = storage.getRequests(RequestStatus.SIGNED).mapNotNull {
            it.certData?.certPath?.let { certs -> it.requestId to certs }
        }.toMap()
        jiraClient.updateSignedRequests(signedRequests)
    }

    /**
     * Creates Jira tickets for all request in [RequestStatus.NEW] state.
     *
     * Usually requests are expected to move to the [RequestStatus.TICKET_CREATED] state immediately,
     * they might be left in the [RequestStatus.NEW] state if Jira is down.
     */
    override fun createTickets() {
        try {
            for (signingRequest in storage.getRequests(RequestStatus.NEW)) {
                createTicket(signingRequest)
            }
        } catch (e: Exception) {
            log.warn("There were errors while creating Jira tickets", e)
        }
    }

    private fun createTicket(signingRequest: CertificateSigningRequest) {
        jiraClient.createRequestTicket(signingRequest.requestId, signingRequest.request)
        storage.markRequestTicketCreated(signingRequest.requestId)
    }
}
