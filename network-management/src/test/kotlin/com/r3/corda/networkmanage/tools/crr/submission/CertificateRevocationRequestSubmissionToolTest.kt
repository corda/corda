/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.tools.crr.submission

import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.doorman.NetworkManagementWebServer
import com.r3.corda.networkmanage.doorman.signer.CrrHandler
import com.r3.corda.networkmanage.doorman.webservice.CertificateRevocationRequestWebService
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.network.CertificateRevocationRequest
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.freeLocalHostAndPort
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.net.URL
import java.security.cert.CRLReason

class CertificateRevocationRequestSubmissionToolTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val hostAndPort = freeLocalHostAndPort()
    private lateinit var webServer: NetworkManagementWebServer
    private lateinit var inputReader: InputReader

    @Before
    fun setUp() {
        inputReader = mock()
    }

    @After
    fun close() {
        webServer.close()
    }

    @Test
    fun `submit request succeeds`() {
        // given
        val request = CertificateRevocationRequest(
                certificateSerialNumber = BigInteger.TEN,
                csrRequestId = "TestCSRId",
                legalName = CordaX500Name.parse("O=TestOrg, C=GB, L=London"),
                reason = CRLReason.KEY_COMPROMISE,
                reporter = "TestReporter"
        )

        givenUserConsoleSequentialInputOnReadLine(request.certificateSerialNumber.toString(),
                request.csrRequestId!!,
                request.legalName.toString(),
                "${request.reason.ordinal + 1}",
                request.reporter)

        val requestId = SecureHash.randomSHA256().toString()
        val requestProcessor = mock<CrrHandler> {
            on { saveRevocationRequest(eq(request)) }.then { requestId }
        }
        startSigningServer(requestProcessor)

        // when
        submit(URL("http://$hostAndPort/certificate-revocation-request"), inputReader)

        // then
        verify(requestProcessor).saveRevocationRequest(eq(request))
    }

    private fun givenUserConsoleSequentialInputOnReadLine(vararg inputs: String) {
        var sequence = whenever(inputReader.readLine()).thenReturn(inputs.first())
        inputs.drop(1).forEach {
            sequence = sequence.thenReturn(it)
        }
    }

    private fun startSigningServer(handler: CrrHandler) {
        webServer = NetworkManagementWebServer(hostAndPort, CertificateRevocationRequestWebService(handler))
        webServer.start()
    }
}