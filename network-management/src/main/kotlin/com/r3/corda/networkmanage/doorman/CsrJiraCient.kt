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
import com.r3.corda.networkmanage.common.utils.getCertRole
import com.r3.corda.networkmanage.common.utils.getEmail
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.security.cert.CertPath
import javax.security.auth.x500.X500Principal

class CsrJiraClient(restClient: JiraRestClient, projectCode: String) : JiraClient(restClient, projectCode) {
    companion object {
        val logger = contextLogger()
    }

    // TODO: Pass in a parsed object instead of raw PKCS10 request.
    fun createCertificateSigningRequestTicket(requestId: String, signingRequest: PKCS10CertificationRequest) {
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
                "Common Name" to subject.commonName,
                "Organisation" to subject.organisation,
                "Organisation Unit" to subject.organisationUnit,
                "State" to subject.state,
                "Nearest City" to subject.locality,
                "Country" to subject.country,
                "Email" to email,
                "X500 Name" to subject.toString())

        val ticketDescription = data.filter { it.value != null }.map { "${it.key}: ${it.value}" }.joinToString("\n") + "\n\n{code}$request{code}"

        val issue = IssueInputBuilder().setIssueTypeId(taskIssueType.id)
                .setProjectKey(projectCode)
                .setDescription(ticketDescription)
                .setSummary(ticketSummary)
                .setFieldValue(requestIdField.id, requestId)
        // This will block until the issue is created.
        restClient.issueClient.createIssue(issue.build()).fail { logger.error("Exception when creating JIRA issue.", it) }.claim()
    }

    fun updateDoneCertificateSigningRequest(requestId: String, certPath: CertPath) {
        // Retrieving certificates for signed CSRs to attach to the jira tasks.
        val certificate = certPath.certificates.first()
        val issue = requireNotNull(getIssueById(requestId)) { "Cannot find the JIRA ticket `request ID` = $requestId" }
        restClient.issueClient.transition(issue, TransitionInput(getTransitionId(DONE_TRANSITION_KEY, issue))).fail { logger.error("Exception when transiting JIRA status.", it) }.claim()
        restClient.issueClient.addAttachment(issue.attachmentsUri, certificate.encoded.inputStream(), "${X509Utilities.CORDA_CLIENT_CA}.cer")
                .fail { logger.error("Error processing request '${issue.key}' : Exception when uploading attachment to JIRA.", it) }.claim()
    }
}
