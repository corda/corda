package com.r3.corda.networkmanage.doorman

import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.r3.corda.networkmanage.common.utils.buildCertPath
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.net.URI

@Ignore
// This is manual test for testing Jira API.
class JiraClientTest {
    private lateinit var jiraClient: JiraClient
    @Before
    fun init() {
        val jiraWebAPI = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI("http://jira.url.com"), "username", "password")
        jiraClient = JiraClient(jiraWebAPI, "DOOR")
    }

    @Test
    fun createRequestTicket() {
        val request = X509Utilities.createCertificateSigningRequest(CordaX500Name("JiraAPITest", "R3 Ltd 3", "London", "GB").x500Principal, "test@test.com", Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        jiraClient.createRequestTicket(SecureHash.randomSHA256().toString(), request)
    }

    @Test
    fun getApprovedRequests() {
        jiraClient.getApprovedRequests().forEach { println(it) }
    }

    @Test
    fun getRejectedRequests() {
        val requests = jiraClient.getRejectedRequests()
        requests.forEach { println(it) }
    }

    @Test
    fun updateSignedRequests() {
        val requests = jiraClient.getApprovedRequests()
        val selfSignedCA = X509Utilities.createSelfSignedCACertificate(CordaX500Name("test", "london", "GB").x500Principal, Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        jiraClient.updateSignedRequests(requests.map { it.requestId to buildCertPath(selfSignedCA) }.toMap())
    }

    @Test
    fun updateRejectedRequests() {
        val requests = jiraClient.getRejectedRequests()
        jiraClient.updateRejectedRequests(requests.map { it.requestId })

        assert(jiraClient.getRejectedRequests().isEmpty())
    }
}
