/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestData
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger

class CrrJiraClient(restClient: JiraRestClient, projectCode: String) : JiraClient(restClient, projectCode) {
    companion object {
        val logger = contextLogger()
    }

    fun createCertificateRevocationRequestTicket(revocationRequest: CertificateRevocationRequestData) {
        // Check there isn't already a ticket for this request.
        if (getIssueById(revocationRequest.requestId) != null) {
            logger.warn("There is already a ticket corresponding to request Id ${revocationRequest.requestId}, not creating a new one.")
            return
        }
        val ticketDescription = "Legal name: ${revocationRequest.legalName}\n" +
                "Certificate serial number: ${revocationRequest.certificateSerialNumber}\n" +
                "Revocation reason: ${revocationRequest.reason.name}\n" +
                "Reporter: ${revocationRequest.reporter}\n" +
                "Original CSR request ID: ${revocationRequest.certificateSigningRequestId}"

        val subject = CordaX500Name.build(revocationRequest.certificate.subjectX500Principal)
        val ticketSummary = if (subject.organisationUnit != null) {
            "${subject.organisationUnit}, ${subject.organisation}"
        } else {
            subject.organisation
        }

        val issue = IssueInputBuilder().setIssueTypeId(taskIssueType.id)
                .setProjectKey(projectCode)
                .setDescription(ticketDescription)
                .setSummary(ticketSummary)
                .setFieldValue(requestIdField.id, revocationRequest.requestId)
        // This will block until the issue is created.
        restClient.issueClient.createIssue(issue.build()).fail { logger.error("Exception when creating JIRA issue.", it) }.claim().key
        val createdIssue = checkNotNull(getIssueById(revocationRequest.requestId)) { "Missing the JIRA ticket for the request ID: ${revocationRequest.requestId}" }
        restClient.issueClient.addAttachment(createdIssue.attachmentsUri, revocationRequest.certificate.encoded.inputStream(), "${revocationRequest.certificateSerialNumber}.cer")
                .fail { logger.error("Error processing request '${createdIssue.key}' : Exception when uploading attachment to JIRA.", it) }.claim()
    }

    fun updateDoneCertificateRevocationRequest(requestId: String) {
        logger.debug("Marking JIRA ticket with `request ID` = $requestId as DONE.")
        val issue = requireNotNull(getIssueById(requestId)) { "Missing the JIRA ticket for the request ID: $requestId" }
        restClient.issueClient.transition(issue, TransitionInput(getTransitionId(DONE_TRANSITION_KEY, issue))).fail { logger.error("Exception when transiting JIRA status.", it) }.claim()
    }
}
