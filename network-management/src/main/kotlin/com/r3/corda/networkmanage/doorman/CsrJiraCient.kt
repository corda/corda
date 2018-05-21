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
import com.r3.corda.networkmanage.common.utils.getCertRole
import com.r3.corda.networkmanage.common.utils.getEmail
import net.corda.core.identity.CordaX500Name
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import javax.security.auth.x500.X500Principal

class CsrJiraClient(restClient: JiraRestClient, projectCode: String) : JiraClient(restClient, projectCode) {
    fun createCertificateSigningRequestTicket(requestData: CertificationRequestData) {
        val (requestId, signingRequest) = requestData

        // TODO The subject of the signing request has already been validated and parsed into a CordaX500Name. We shouldn't
        // have to do it again here.
        val subject = CordaX500Name.build(X500Principal(signingRequest.subject.encoded))

        val ticketSummary = if (subject.organisationUnit != null) {
            "${subject.organisationUnit}, ${subject.organisation}"
        } else {
            subject.organisation
        }

        val data = mapOf(
                "Requested Role Type" to signingRequest.getCertRole().name,
                "Common Name" to subject.commonName,
                "Organisation" to subject.organisation,
                "Organisation Unit" to subject.organisationUnit,
                "State" to subject.state,
                "Nearest City" to subject.locality,
                "Country" to subject.country,
                "Email" to signingRequest.getEmail(),
                "X500 Name" to subject.toString())

        val requestPemString = StringWriter().apply {
            JcaPEMWriter(this).use {
                it.writeObject(PemObject("CERTIFICATE REQUEST", signingRequest.encoded))
            }
        }.toString()

        val ticketDescription = data.filter { it.value != null }.map { "${it.key}: ${it.value}" }.joinToString("\n") + "\n\n{code}$requestPemString{code}"

        val issue = IssueInputBuilder().setIssueTypeId(taskIssueType.id)
                .setProjectKey(projectCode)
                .setDescription(ticketDescription)
                .setSummary(ticketSummary)
                .setFieldValue(requestIdField.id, requestId)

        createJiraTicket(requestId, issue.build())
    }
}

// TODO: Parse PKCS10 request.
data class CertificationRequestData(val requestId: String, val rawRequest: PKCS10CertificationRequest)


