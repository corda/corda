/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.signer

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.*
import com.r3.corda.networkmanage.doorman.ApprovedRequest
import com.r3.corda.networkmanage.doorman.CertificationRequestData
import com.r3.corda.networkmanage.doorman.CsrJiraClient
import com.r3.corda.networkmanage.doorman.RejectedRequest
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.security.cert.CertPath
import kotlin.test.assertEquals

class JiraCsrHandlerTest : TestBase() {
    @Rule
    @JvmField
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var jiraClient: CsrJiraClient

    @Mock
    private lateinit var certificationRequestStorage: CertificateSigningRequestStorage

    @Mock
    private lateinit var defaultCsrHandler: DefaultCsrHandler

    @Mock
    private val certPath: CertPath = mock()

    private lateinit var jiraCsrHandler: JiraCsrHandler
    private val requestId = "id"
    private lateinit var certificateResponse: CertificateResponse.Ready

    private val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val pkcS10CertificationRequest = X509Utilities.createCertificateSigningRequest(
            CordaX500Name(locality = "London", organisation = "LegalName", country = "GB").x500Principal,
            "my@mail.com",
            keyPair)

    @Before
    fun setup() {
        jiraCsrHandler = JiraCsrHandler(jiraClient, certificationRequestStorage, defaultCsrHandler)
        certificateResponse = CertificateResponse.Ready(certPath)
    }

    @Test
    fun `If jira connection fails we don't mark the ticket as created`() {
        whenever(defaultCsrHandler.saveRequest(any())).thenReturn(requestId)
        whenever(defaultCsrHandler.getResponse(requestId)).thenReturn(certificateResponse)
        whenever(jiraClient.createCertificateSigningRequestTicket(any())).thenThrow(IllegalStateException("something broke"))

        // Test
        jiraCsrHandler.saveRequest(pkcS10CertificationRequest)

        verify(certificationRequestStorage, never()).markRequestTicketCreated(requestId)
    }

    @Test
    fun `If jira connection works we mark the ticket as created`() {
        whenever(defaultCsrHandler.saveRequest(any())).thenReturn(requestId)
        whenever(defaultCsrHandler.getResponse(requestId)).thenReturn(certificateResponse)

        // Test
        jiraCsrHandler.saveRequest(pkcS10CertificationRequest)

        verify(certificationRequestStorage, times(1)).markRequestTicketCreated(requestId)
    }

    @Test
    fun `create tickets`() {
        val csr = certificateSigningRequest(
                requestId = requestId,
                legalName = CordaX500Name.parse("O=Test Org., C=GB, L=London"),
                status = RequestStatus.NEW,
                request = pkcS10CertificationRequest)
        whenever(certificationRequestStorage.getRequests(RequestStatus.NEW)).thenReturn(listOf(csr))
        // Test
        jiraCsrHandler.processRequests()

        verify(jiraClient).createCertificateSigningRequestTicket(CertificationRequestData(requestId, csr.request))
        verify(certificationRequestStorage).markRequestTicketCreated(requestId)
    }

    @Test
    fun `sync tickets status`() {
        val id1 = SecureHash.randomSHA256().toString()
        val id2 = SecureHash.randomSHA256().toString()
        val csr1 = CertificateSigningRequest(id1, CordaX500Name.parse("O=Test1 Org., C=GB, L=London"), SecureHash.randomSHA256(), RequestStatus.NEW, pkcS10CertificationRequest, null, "Test", null)
        val csr2 = CertificateSigningRequest(id2, CordaX500Name.parse("O=Test2 Org., C=GB, L=London"), SecureHash.randomSHA256(), RequestStatus.NEW, pkcS10CertificationRequest, null, "Test", null)

        val requests = mutableMapOf(id1 to csr1, id2 to csr2)

        // Mocking storage behaviour.
        whenever(certificationRequestStorage.getRequests(RequestStatus.NEW)).thenReturn(requests.values.filter { it.status == RequestStatus.NEW })
        whenever(certificationRequestStorage.getRequest(any())).thenAnswer { requests[it.getArgument(0)] }
        whenever(certificationRequestStorage.approveRequest(any(), any())).then {
            val id = it.getArgument<String>(0)
            if (requests[id]?.status == RequestStatus.NEW) {
                requests[id] = requests[id]!!.copy(status = RequestStatus.APPROVED, modifiedBy = it.getArgument(1))
            }
            null
        }
        whenever(certificationRequestStorage.rejectRequest(any(), any(), any())).then {
            val id = it.getArgument<String>(0)
            requests[id] = requests[id]!!.copy(status = RequestStatus.REJECTED, modifiedBy = it.getArgument(1), remark = it.getArgument(2))
            null
        }

        // Status change from jira.
        whenever(jiraClient.getApprovedRequests()).thenReturn(listOf(ApprovedRequest(id1, "Me")))
        whenever(jiraClient.getRejectedRequests()).thenReturn(listOf(RejectedRequest(id2, "Me", "Test reject")))

        // Test.
        jiraCsrHandler.processRequests()

        verify(jiraClient).createCertificateSigningRequestTicket(CertificationRequestData(id1, csr1.request))
        verify(jiraClient).createCertificateSigningRequestTicket(CertificationRequestData(id2, csr2.request))

        verify(certificationRequestStorage).markRequestTicketCreated(id1)
        verify(certificationRequestStorage).markRequestTicketCreated(id2)

        // Verify request has the correct status in DB.
        assertEquals(RequestStatus.APPROVED, requests[id1]!!.status)
        assertEquals(RequestStatus.REJECTED, requests[id2]!!.status)

        // Verify jira client get the correct call.
        verify(jiraClient).updateRejectedRequest(id2)
        verify(jiraClient, never()).transitRequestStatusToDone(any(), anyOrNull())

        // Sign request 1
        val certPath = mock<CertPath>()
        val certData = CertificateData(CertificateStatus.VALID, certPath)
        requests[id1] = requests[id1]!!.copy(status = RequestStatus.DONE, certData = certData)

        // Process request again.
        jiraCsrHandler.processRequests()

        // Update signed request should be called.
        verify(jiraClient).transitRequestStatusToDone(eq(id1), anyOrNull())
    }
}
