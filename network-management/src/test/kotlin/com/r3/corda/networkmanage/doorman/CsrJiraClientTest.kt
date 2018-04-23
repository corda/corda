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

import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.net.URI
import javax.security.auth.x500.X500Principal

@Ignore
// This is manual test for testing Jira API.
class CsrJiraClientTest {
    private lateinit var jiraClient: CsrJiraClient
    @Before
    fun init() {
        val jiraWebAPI = AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(URI("http://jira.url.com"), "username", "password")
        jiraClient = CsrJiraClient(jiraWebAPI, "DOOR")
    }

    @Test
    fun createRequestTicket() {
        val request = X509Utilities.createCertificateSigningRequest(CordaX500Name("JiraAPITest", "R3 Ltd 3", "London", "GB").x500Principal, "test@test.com", Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME))
        jiraClient.createCertificateSigningRequestTicket(SecureHash.randomSHA256().toString(), request)
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
        val selfSignedCaCertPath = X509Utilities.buildCertPath(X509Utilities.createSelfSignedCACertificate(
                X500Principal("O=test,L=london,C=GB"),
                Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)))
        jiraClient.getApprovedRequests().forEach {
            jiraClient.updateDoneCertificateSigningRequest(it.requestId, selfSignedCaCertPath)
        }
    }

    @Test
    fun updateRejectedRequests() {
        jiraClient.getRejectedRequests().forEach {
            jiraClient.updateRejectedRequest(it.requestId)
        }

        assert(jiraClient.getRejectedRequests().isEmpty())
    }
}
