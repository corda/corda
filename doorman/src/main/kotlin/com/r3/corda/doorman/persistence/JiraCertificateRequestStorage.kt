package com.r3.corda.doorman.persistence

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.commonName
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.security.cert.Certificate

class JiraCertificateRequestStorage(val delegate: CertificationRequestStorage, val jiraClient: JiraRestClient, val projectCode: String, val doneTransitionCode: Int) : CertificationRequestStorage by delegate {
    companion object {
        val APPROVED = "Approved"
        val REJECTED = "Rejected"
    }

    // The JIRA project must have a Request ID field and the Task issue type.
    private val requestIdField = jiraClient.metadataClient.fields.claim().find { it.name == "Request ID" }!!
    private val taskIssueType = jiraClient.metadataClient.issueTypes.claim().find { it.name == "Task" }!!

    override fun saveRequest(certificationData: CertificationRequestData): String {
        val requestId = delegate.saveRequest(certificationData)
        // Make sure request has been accepted.
        val response = getResponse(requestId)
        if (response !is CertificateResponse.Unauthorised) {
            val request = StringWriter().use {
                JcaPEMWriter(it).use {
                    it.writeObject(PemObject("CERTIFICATE REQUEST", certificationData.request.encoded))
                }
                it.toString()
            }
            val commonName = certificationData.request.subject.commonName
            val email = certificationData.request.subject.getRDNs(BCStyle.EmailAddress).firstOrNull()?.first?.value
            val nearestCity = certificationData.request.subject.getRDNs(BCStyle.L).firstOrNull()?.first?.value

            val issue = IssueInputBuilder().setIssueTypeId(taskIssueType.id)
                    .setProjectKey("TD")
                    .setDescription("Legal Name: $commonName\nNearest City: $nearestCity\nEmail: $email\n\n{code}$request{code}")
                    .setSummary(commonName)
                    .setFieldValue(requestIdField.id, requestId)

            jiraClient.issueClient.createIssue(issue.build()).fail(Throwable::printStackTrace).claim()
        }
        return requestId
    }

    override fun approveRequest(requestId: String, generateCertificate: CertificationRequestData.() -> Certificate) {
        delegate.approveRequest(requestId, generateCertificate)
        val certificate = (getResponse(requestId) as? CertificateResponse.Ready)?.certificate
        val issue = jiraClient.searchClient.searchJql("'Request ID' ~ $requestId").claim().issues.firstOrNull()
        issue?.let {
            jiraClient.issueClient.transition(it, TransitionInput(doneTransitionCode)).fail(Throwable::printStackTrace)
            jiraClient.issueClient.addAttachment(it.attachmentsUri, ByteArrayInputStream(certificate?.encoded), "${X509Utilities.CORDA_CLIENT_CA}.cer")
        }
    }

    fun getRequestByStatus(status: String): List<String> {
        val issues = jiraClient.searchClient.searchJql("project = $projectCode AND status = $status").claim().issues
        return issues.map { it.getField(requestIdField.id)?.value?.toString() }.filterNotNull()
    }
}
