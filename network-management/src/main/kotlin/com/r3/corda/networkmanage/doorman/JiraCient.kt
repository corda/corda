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

import com.atlassian.jira.rest.client.api.IssueRestClient
import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Comment
import com.atlassian.jira.rest.client.api.domain.Field
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.IssueType
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import com.r3.corda.networkmanage.common.utils.getCertRole
import com.r3.corda.networkmanage.common.utils.getEmail
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.security.cert.CertPath
import javax.security.auth.x500.X500Principal

class JiraClient(private val restClient: JiraRestClient, private val projectCode: String) {
    companion object {
        val logger = contextLogger()
    }

    // The JIRA project must have a Request ID and reject reason field, and the Task issue type.
    private val requestIdField: Field = restClient.metadataClient.fields.claim().find { it.name == "Request ID" } ?: throw IllegalArgumentException("Request ID field not found in JIRA '$projectCode'")
    private val rejectReasonField: Field = restClient.metadataClient.fields.claim().find { it.name == "Reject Reason" } ?: throw IllegalArgumentException("Reject Reason field not found in JIRA '$projectCode'")
    private val taskIssueType: IssueType = restClient.metadataClient.issueTypes.claim().find { it.name == "Task" } ?: throw IllegalArgumentException("Task issue type field not found in JIRA '$projectCode'")

    private var doneTransitionId: Int = -1
    private var canceledTransitionId: Int = -1
    private var startProgressTransitionId: Int = -1

    // TODO: Pass in a parsed object instead of raw PKCS10 request.
    fun createRequestTicket(requestId: String, signingRequest: PKCS10CertificationRequest) {
        // Check there isn't already a ticket for this request.
        if (getIssueById(requestId) != null) {
            logger.warn("There is already a ticket corresponding to request Id $requestId, not creating a new one.")
            return
        }

        // Make sure request has been accepted.
        val request = StringWriter()
        JcaPEMWriter(request).use {
            it.writeObject(PemObject("CERTIFICATE REQUEST", signingRequest.encoded))
        }

        // TODO The subject of the signing request has already been validated and parsed into a CordaX500Name. We shouldn't
        // have to do it again here.
        val subject = CordaX500Name.build(X500Principal(signingRequest.subject.encoded))
        val email = signingRequest.getEmail()

        val certRole = signingRequest.getCertRole()

        val ticketSummary = if (subject.organisationUnit != null) {
            "${subject.organisationUnit}, ${subject.organisation}"
        } else {
            subject.organisation
        }

        val data = mapOf("Requested Role Type" to certRole.name,
                "Organisation" to subject.organisation,
                "Organisation Unit" to subject.organisationUnit,
                "Nearest City" to subject.locality,
                "Country" to subject.country,
                "Email" to email)

        val ticketDescription = data.filter { it.value != null }.map { "${it.key}: ${it.value}" }.joinToString("\n") + "\n\n{code}$request{code}"

        val issue = IssueInputBuilder().setIssueTypeId(taskIssueType.id)
                .setProjectKey(projectCode)
                .setDescription(ticketDescription)
                .setSummary(ticketSummary)
                .setFieldValue(requestIdField.id, requestId)
        // This will block until the issue is created.
        restClient.issueClient.createIssue(issue.build()).fail { logger.error("Exception when creating JIRA issue.", it) }.claim()
    }

    fun getApprovedRequests(): List<ApprovedRequest> {
        val issues = restClient.searchClient.searchJql("project = $projectCode AND status = Approved").claim().issues
        return issues.mapNotNull { issue ->
            val requestId = issue.getField(requestIdField.id)?.value?.toString() ?: throw IllegalArgumentException("Error processing request '${issue.key}' : RequestId cannot be null.")
            // Issue retrieved via search doesn't contain change logs.
            val fullIssue = restClient.issueClient.getIssue(issue.key, listOf(IssueRestClient.Expandos.CHANGELOG)).claim()
            val approvedBy = fullIssue.changelog?.last { it.items.any { it.field == "status" && it.toString == "Approved" } }
            ApprovedRequest(requestId, approvedBy?.author?.displayName ?: "Unknown")
        }
    }

    fun getRejectedRequests(): List<RejectedRequest> {
        val issues = restClient.searchClient.searchJql("project = $projectCode AND status = Rejected").claim().issues
        return issues.mapNotNull { issue ->
            val requestId = issue.getField(requestIdField.id)?.value?.toString() ?: throw IllegalArgumentException("Error processing request '${issue.key}' : RequestId cannot be null.")
            val rejectedReason = issue.getField(rejectReasonField.id)?.value?.toString()
            // Issue retrieved via search doesn't contain comments.
            val fullIssue = restClient.issueClient.getIssue(issue.key, listOf(IssueRestClient.Expandos.CHANGELOG)).claim()
            val rejectedBy = fullIssue.changelog?.last { it.items.any { it.field == "status" && it.toString == "Rejected" } }
            RejectedRequest(requestId, rejectedBy?.author?.displayName ?: "Unknown", rejectedReason)
        }
    }

    fun updateSignedRequests(signedRequests: Map<String, CertPath>) {
        // Retrieving certificates for signed CSRs to attach to the jira tasks.
        signedRequests.forEach { (id, certPath) ->
            val certificate = certPath.certificates.first()
            val issue = getIssueById(id)
            if (issue != null) {
                if (doneTransitionId == -1) {
                    doneTransitionId = restClient.issueClient.getTransitions(issue.transitionsUri).claim().single { it.name == "Done" }.id
                }
                restClient.issueClient.transition(issue, TransitionInput(doneTransitionId)).fail { logger.error("Exception when transiting JIRA status.", it) }.claim()
                restClient.issueClient.addAttachment(issue.attachmentsUri, certificate.encoded.inputStream(), "${X509Utilities.CORDA_CLIENT_CA}.cer")
                        .fail { logger.error("Error processing request '${issue.key}' : Exception when uploading attachment to JIRA.", it) }.claim()
            }
        }
    }

    fun updateRejectedRequests(rejectedRequests: List<String>) {
        rejectedRequests.mapNotNull { getIssueById(it) }
                .forEach { issue ->
                    // Move status to in progress.
                    if (startProgressTransitionId == -1) {
                        startProgressTransitionId = restClient.issueClient.getTransitions(issue.transitionsUri).claim().single { it.name == "Start Progress" }.id
                    }
                    restClient.issueClient.transition(issue, TransitionInput(startProgressTransitionId)).fail { logger.error("Error processing request '${issue.key}' : Exception when transiting JIRA status.", it) }.claim()
                    // Move status to cancelled.
                    if (canceledTransitionId == -1) {
                        canceledTransitionId = restClient.issueClient.getTransitions(issue.transitionsUri).claim().single { it.name == "Stop Progress" }.id
                    }
                    restClient.issueClient.transition(issue, TransitionInput(canceledTransitionId)).fail { logger.error("Error processing request '${issue.key}' : Exception when transiting JIRA status.", it) }.claim()
                    restClient.issueClient.addComment(issue.commentsUri, Comment.valueOf("Request cancelled by doorman.")).claim()
                }
    }

    private fun getIssueById(requestId: String): Issue? {
        // Jira only support ~ (contains) search for custom textfield.
        return restClient.searchClient.searchJql("'Request ID' ~ $requestId").claim().issues.firstOrNull()
    }
}

data class ApprovedRequest(val requestId: String, val approvedBy: String)

data class RejectedRequest(val requestId: String, val rejectedBy: String, val reason: String?)
