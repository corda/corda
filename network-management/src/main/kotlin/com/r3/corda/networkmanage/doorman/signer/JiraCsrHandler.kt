package com.r3.corda.networkmanage.doorman.signer

import com.r3.corda.networkmanage.common.persistence.CertificateResponse
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequest
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.doorman.JiraClient
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.loggerFor
import org.bouncycastle.pkcs.PKCS10CertificationRequest

class JiraCsrHandler(private val jiraClient: JiraClient, private val storage: CertificationRequestStorage, private val delegate: CsrHandler) : CsrHandler by delegate {
    private companion object {
        val log = contextLogger()
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
        val approvedRequest = jiraClient.getApprovedRequests()
        approvedRequest.forEach { (id, approvedBy) -> storage.approveRequest(id, approvedBy) }
        delegate.processApprovedRequests()

        val signedRequests = approvedRequest.mapNotNull { (id, _) ->
            val request = storage.getRequest(id)

            if (request != null && request.status == RequestStatus.SIGNED) {
                request.certData?.certPath?.let { certs -> id to certs }
            } else {
                null
            }
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
