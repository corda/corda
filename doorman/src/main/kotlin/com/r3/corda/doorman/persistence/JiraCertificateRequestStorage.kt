package com.r3.corda.doorman.persistence

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Field
import com.atlassian.jira.rest.client.api.domain.IssueType
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.commonName
import net.corda.core.utilities.loggerFor
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.security.cert.Certificate

class JiraCertificateRequestStorage(val delegate: CertificationRequestStorage,
                                    val jiraClient: JiraRestClient,
                                    val projectCode: String,
                                    val doneTransitionCode: Int) : CertificationRequestStorage by delegate {
    private enum class Status {
        Approved, Rejected
    }

    companion object{
        private val logger = loggerFor<JiraCertificateRequestStorage>()
    }

    // The JIRA project must have a Request ID field and the Task issue type.
    private val requestIdField: Field = jiraClient.metadataClient.fields.claim().find { it.name == "Request ID" }!!
    private val taskIssueType: IssueType = jiraClient.metadataClient.issueTypes.claim().find { it.name == "Task" }!!

    override fun saveRequest(certificationData: CertificationRequestData): String {
        val requestId = delegate.saveRequest(certificationData)
        // Make sure request has been accepted.
        val response = getResponse(requestId)
        if (response !is CertificateResponse.Unauthorised) {
            val request = StringWriter()
            JcaPEMWriter(request).writeObject(PemObject("CERTIFICATE REQUEST", certificationData.request.encoded))
            val commonName = certificationData.request.subject.commonName
            val email = certificationData.request.subject.getRDNs(BCStyle.EmailAddress).firstOrNull()?.first?.value
            val nearestCity = certificationData.request.subject.getRDNs(BCStyle.L).firstOrNull()?.first?.value

            val issue = IssueInputBuilder().setIssueTypeId(taskIssueType.id)
                    .setProjectKey(projectCode)
                    .setDescription("Legal Name: $commonName\nNearest City: $nearestCity\nEmail: $email\n\n{code}$request{code}")
                    .setSummary(commonName)
                    .setFieldValue(requestIdField.id, requestId)
            // This will block until the issue is created.
            jiraClient.issueClient.createIssue(issue.build()).fail { logger.error("Exception when creating JIRA issue.", it) }.claim()
        }
        return requestId
    }

    override fun approveRequest(requestId: String, generateCertificate: CertificationRequestData.() -> Certificate) {
        delegate.approveRequest(requestId, generateCertificate)
        // Certificate should be created, retrieving it to attach to the jira task.
        val certificate = (getResponse(requestId) as? CertificateResponse.Ready)?.certificate
        // Jira only support ~ (contains) search for custom textfield.
        val issue = jiraClient.searchClient.searchJql("'Request ID' ~ $requestId").claim().issues.firstOrNull()
        if (issue != null) {
            jiraClient.issueClient.transition(issue, TransitionInput(doneTransitionCode)).fail { logger.error("Exception when transiting JIRA status.", it) }.claim()
            jiraClient.issueClient.addAttachment(issue.attachmentsUri, certificate?.encoded?.inputStream(), "${X509Utilities.CORDA_CLIENT_CA}.cer")
                    .fail { logger.error("Exception when uploading attachment to JIRA.", it) }.claim()
        }
    }

    override fun getApprovedRequestIds(): List<String> = getRequestByStatus(Status.Approved)

    private fun getRequestByStatus(status: Status): List<String> {
        val issues = jiraClient.searchClient.searchJql("project = $projectCode AND status = $status").claim().issues
        return issues.map { it.getField(requestIdField.id)?.value?.toString() }.filterNotNull()
    }
}
