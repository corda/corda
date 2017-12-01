package com.r3.corda.networkmanage.doorman.signer

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.common.persistence.CertificateResponse
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequest
import com.r3.corda.networkmanage.common.persistence.CertificationRequestStorage
import com.r3.corda.networkmanage.common.persistence.RequestStatus
import com.r3.corda.networkmanage.doorman.JiraClient
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import java.security.cert.CertPath

class JiraCsrHandlerTest {

    @Rule
    @JvmField
    val mockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var jiraClient: JiraClient

    @Mock
    lateinit var certificationRequestStorage: CertificationRequestStorage

    @Mock
    lateinit var defaultCsrHandler: DefaultCsrHandler

    @Mock
    var certPath: CertPath = mock()

    private lateinit var jiraCsrHandler: JiraCsrHandler
    private val requestId = "id"
    private lateinit var certificateResponse: CertificateResponse.Ready

    private val keyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val pkcS10CertificationRequest = X509Utilities.createCertificateSigningRequest(CordaX500Name(locality = "London", organisation = "LegalName", country = "GB"), "my@mail.com", keyPair)

    @Before
    fun setup() {
        jiraCsrHandler = JiraCsrHandler(jiraClient, certificationRequestStorage, defaultCsrHandler)
        certificateResponse = CertificateResponse.Ready(certPath)
    }

    @Test
    fun `If jira connection fails we don't mark the ticket as created`() {
        whenever(defaultCsrHandler.saveRequest(any())).thenReturn(requestId)
        whenever(defaultCsrHandler.getResponse(requestId)).thenReturn(certificateResponse)
        whenever(jiraClient.createRequestTicket(eq(requestId), any())).thenThrow(IllegalStateException("something broke"))

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
        val csr = CertificateSigningRequest(requestId, "name", RequestStatus.NEW, pkcS10CertificationRequest, null, emptyList(), null)
        whenever(certificationRequestStorage.getRequests(RequestStatus.NEW)).thenReturn(listOf(csr))

        // Test
        jiraCsrHandler.createTickets()

        verify(jiraClient).createRequestTicket(requestId, csr.request)
        verify(certificationRequestStorage).markRequestTicketCreated(requestId)
    }
}