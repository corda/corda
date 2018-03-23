/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.signer

import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestData
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestStorage
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.doorman.ApprovedRequest
import com.r3.corda.networkmanage.doorman.CrrJiraClient
import com.r3.corda.networkmanage.doorman.RejectedRequest
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.network.CertificateRevocationRequest

class JiraCrrHandler(private val jiraClient: CrrJiraClient,
                     private val crrStorage: CertificateRevocationRequestStorage,
                     private val localCrlHandler: LocalCrlHandler?) : CrrHandler {
    private companion object {
        val logger = contextLogger()
    }

    override fun saveRevocationRequest(request: CertificateRevocationRequest): String {
        try {
            val requestId = crrStorage.saveRevocationRequest(request)
            val requestData = crrStorage.getRevocationRequest(requestId)
            requestData ?: throw IllegalStateException("Request $requestId does not exist.")
            jiraClient.createCertificateRevocationRequestTicket(requestData)
            crrStorage.markRequestTicketCreated(requestId)
            return requestId
        } catch (e: Exception) {
            logger.error("There was an error while creating JIRA tickets", e)
            throw e
        }
    }

    override fun processRequests() {
        createTickets()
        val (approvedRequests, rejectedRequests) = updateRequestStatuses()
        updateJiraTickets(approvedRequests, rejectedRequests)
        localCrlHandler?.signCrl()
    }

    private fun updateRequestStatuses(): Pair<List<ApprovedRequest>, List<RejectedRequest>> {
        // Update local request statuses.
        val approvedRequest = jiraClient.getApprovedRequests()
        approvedRequest.forEach { (id, approvedBy) -> crrStorage.approveRevocationRequest(id, approvedBy) }
        val rejectedRequest = jiraClient.getRejectedRequests()
        rejectedRequest.forEach { (id, rejectedBy, reason) -> crrStorage.rejectRevocationRequest(id, rejectedBy, reason) }
        return Pair(approvedRequest, rejectedRequest)
    }

    private fun updateJiraTickets(approvedRequest: List<ApprovedRequest>, rejectedRequest: List<RejectedRequest>) {
        // Reconfirm request status and update jira status
        val doneRequests = approvedRequest.mapNotNull { crrStorage.getRevocationRequest(it.requestId) }
                .filter { it.status == RequestStatus.DONE }
                .map { it.requestId }

        jiraClient.updateDoneCertificateRevocationRequests(doneRequests)

        val rejectedRequestIDs = rejectedRequest.mapNotNull { crrStorage.getRevocationRequest(it.requestId) }
                .filter { it.status == RequestStatus.REJECTED }
                .map { it.requestId }
        jiraClient.updateRejectedRequests(rejectedRequestIDs)
    }

    /**
     * Creates Jira tickets for all request in [RequestStatus.NEW] state.
     *
     * Usually requests are expected to move to the [RequestStatus.TICKET_CREATED] state immediately,
     * they might be left in the [RequestStatus.NEW] state if Jira is down.
     */
    private fun createTickets() {
        crrStorage.getRevocationRequests(RequestStatus.NEW).forEach {
            try {
                createTicket(it)
            } catch (e: Exception) {
                logger.warn("There were errors while creating Jira tickets for request '${it.requestId}'", e)
            }
        }
    }

    private fun createTicket(revocationRequest: CertificateRevocationRequestData) {
        jiraClient.createCertificateRevocationRequestTicket(revocationRequest)
        crrStorage.markRequestTicketCreated(revocationRequest.requestId)
    }
}
