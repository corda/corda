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
import com.r3.corda.networkmanage.common.persistence.CertificateRevocationRequestData
import net.corda.core.identity.CordaX500Name

class CrrJiraClient(restClient: JiraRestClient, projectCode: String) : JiraClient(restClient, projectCode) {
    fun createCertificateRevocationRequestTicket(revocationRequest: CertificateRevocationRequestData) {
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

        val attachment = Pair("${revocationRequest.certificateSerialNumber}.cer", revocationRequest.certificate.encoded.inputStream())
        createJiraTicket(revocationRequest.requestId, issue.build(), attachment)
    }
}
