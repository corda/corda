package com.r3.corda.networkmanage.doorman

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Field
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.IssueType
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import net.corda.core.internal.country
import net.corda.core.internal.locality
import net.corda.core.internal.organisation
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.X509Utilities
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter
import java.security.cert.CertPath

class JiraClient(private val restClient: JiraRestClient, private val projectCode: String, private val doneTransitionCode: Int) {
    companion object {
        val logger = loggerFor<JiraClient>()
    }

    // The JIRA project must have a Request ID field and the Task issue type.
    private val requestIdField: Field = restClient.metadataClient.fields.claim().find { it.name == "Request ID" } ?: throw IllegalArgumentException("Request ID field not found in JIRA '$projectCode'")
    private val taskIssueType: IssueType = restClient.metadataClient.issueTypes.claim().find { it.name == "Task" } ?: throw IllegalArgumentException("Task issue type field not found in JIRA '$projectCode'")

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
        val organisation = signingRequest.subject.organisation
        val nearestCity = signingRequest.subject.locality
        val country = signingRequest.subject.country

        val email = signingRequest.getAttributes(BCStyle.E).firstOrNull()?.attrValues?.firstOrNull()?.toString()

        val issue = IssueInputBuilder().setIssueTypeId(taskIssueType.id)
                .setProjectKey(projectCode)
                .setDescription("Organisation: $organisation\nNearest City: $nearestCity\nCountry: $country\nEmail: $email\n\n{code}$request{code}")
                .setSummary(organisation)
                .setFieldValue(requestIdField.id, requestId)
        // This will block until the issue is created.
        restClient.issueClient.createIssue(issue.build()).fail { logger.error("Exception when creating JIRA issue.", it) }.claim()
    }

    fun getApprovedRequests(): List<Pair<String, String>> {
        val issues = restClient.searchClient.searchJql("project = $projectCode AND status = Approved").claim().issues
        return issues.map { issue ->
            issue.getField(requestIdField.id)?.value?.toString().let {
                val requestId = it ?: throw IllegalArgumentException("RequestId cannot be null.")
                val approvedBy = issue.assignee?.displayName ?: "Unknown"
                Pair(requestId, approvedBy)
            }
        }
    }

    fun updateSignedRequests(signedRequests: Map<String, CertPath>) {
        // Retrieving certificates for signed CSRs to attach to the jira tasks.
        signedRequests.forEach { (id, certPath) ->
            val certificate = certPath.certificates.first()
            // Jira only support ~ (contains) search for custom textfield.
            val issue = getIssueById(id)
            if (issue != null) {
                restClient.issueClient.transition(issue, TransitionInput(doneTransitionCode)).fail { logger.error("Exception when transiting JIRA status.", it) }.claim()
                restClient.issueClient.addAttachment(issue.attachmentsUri, certificate?.encoded?.inputStream(), "${X509Utilities.CORDA_CLIENT_CA}.cer")
                        .fail { logger.error("Exception when uploading attachment to JIRA.", it) }.claim()
            }
        }
    }

    private fun getIssueById(requestId: String): Issue? =
            restClient.searchClient.searchJql("'Request ID' ~ $requestId").claim().issues.firstOrNull()
}
