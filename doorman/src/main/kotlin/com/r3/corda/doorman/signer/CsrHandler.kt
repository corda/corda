package com.r3.corda.doorman.signer

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Field
import com.atlassian.jira.rest.client.api.domain.IssueType
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput
import com.r3.corda.doorman.persistence.CertificateResponse
import com.r3.corda.doorman.persistence.CertificationRequestData
import com.r3.corda.doorman.persistence.CertificationRequestStorage
import net.corda.core.utilities.country
import net.corda.core.utilities.locality
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.organisation
import net.corda.node.utilities.X509Utilities
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.io.pem.PemObject
import java.io.StringWriter

open class DefaultCsrHandler(protected val storage: CertificationRequestStorage, protected val signer: Signer) {
    open fun saveRequest(certificationData: CertificationRequestData): String {
        return storage.saveRequest(certificationData)
    }

    open fun sign() {
        for (id in storage.getApprovedRequestIds()) {
            signer.sign(id)
        }
    }

    fun getResponse(requestId: String) = storage.getResponse(requestId)
}

class JiraCsrHandler(private val jiraClient: JiraRestClient,
                     private val projectCode: String,
                     private val doneTransitionCode: Int,
                     storage: CertificationRequestStorage, signer: Signer) : DefaultCsrHandler(storage, signer) {

    companion object {
        private val logger = loggerFor<JiraCsrHandler>()
    }

    // The JIRA project must have a Request ID field and the Task issue type.
    private val requestIdField: Field = jiraClient.metadataClient.fields.claim().find { it.name == "Request ID" }!!
    private val taskIssueType: IssueType = jiraClient.metadataClient.issueTypes.claim().find { it.name == "Task" }!!

    override fun saveRequest(certificationData: CertificationRequestData): String {
        val requestId = super.saveRequest(certificationData)
        // Make sure request has been accepted.
        val response = storage.getResponse(requestId)
        if (response !is CertificateResponse.Unauthorised) {
            val request = StringWriter()
            JcaPEMWriter(request).use {
                it.writeObject(PemObject("CERTIFICATE REQUEST", certificationData.request.encoded))
            }
            val organisation = certificationData.request.subject.organisation
            val nearestCity = certificationData.request.subject.locality
            val country = certificationData.request.subject.country

            val email = certificationData.request.getAttributes(BCStyle.E).firstOrNull()?.attrValues?.firstOrNull()?.toString()

            val issue = IssueInputBuilder().setIssueTypeId(taskIssueType.id)
                    .setProjectKey(projectCode)
                    .setDescription("Organisation: $organisation\nNearest City: $nearestCity\nCountry: $country\nEmail: $email\n\n{code}$request{code}")
                    .setSummary(organisation)
                    .setFieldValue(requestIdField.id, requestId)
            // This will block until the issue is created.
            jiraClient.issueClient.createIssue(issue.build()).fail { logger.error("Exception when creating JIRA issue.", it) }.claim()
        }
        return requestId
    }

    override fun sign() {
        val issues = jiraClient.searchClient.searchJql("project = $projectCode AND status = Approved").claim().issues
        issues.map {
            val requestId = it.getField(requestIdField.id)?.value?.toString()
            if (requestId != null) {
                var approvedBy = it.assignee?.displayName
                if (approvedBy == null) {
                    approvedBy = "Unknown"
                }
                storage.approveRequest(requestId, approvedBy)
            }

        }
        super.sign()
        // Retrieving certificates for signed CSRs to attach to the jira tasks.
        storage.getSignedRequestIds().forEach {
            val certificate = (storage.getResponse(it) as? CertificateResponse.Ready)?.certificatePath!!.certificates.first()
            // Jira only support ~ (contains) search for custom textfield.
            val issue = jiraClient.searchClient.searchJql("'Request ID' ~ $it").claim().issues.firstOrNull()
            if (issue != null) {
                jiraClient.issueClient.transition(issue, TransitionInput(doneTransitionCode)).fail { logger.error("Exception when transiting JIRA status.", it) }.claim()
                jiraClient.issueClient.addAttachment(issue.attachmentsUri, certificate?.encoded?.inputStream(), "${X509Utilities.CORDA_CLIENT_CA}.cer")
                        .fail { logger.error("Exception when uploading attachment to JIRA.", it) }.claim()
            }
        }
    }
}