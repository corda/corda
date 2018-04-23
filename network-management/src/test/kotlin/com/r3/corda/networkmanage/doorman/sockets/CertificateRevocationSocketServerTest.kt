package com.r3.corda.networkmanage.doorman.sockets

import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.common.persistence.*
import com.r3.corda.networkmanage.common.sockets.CrlResponseMessage
import com.r3.corda.networkmanage.common.sockets.CrlRetrievalMessage
import com.r3.corda.networkmanage.common.sockets.CrrsByStatusMessage
import com.r3.corda.networkmanage.common.utils.readObject
import com.r3.corda.networkmanage.common.utils.writeObject
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.x500Name
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.freePort
import net.corda.testing.internal.DEV_INTERMEDIATE_CA
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.net.Socket
import java.security.cert.CRLReason
import java.security.cert.X509CRL
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CertificateRevocationSocketServerTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private lateinit var crlStorage: CertificateRevocationListStorage
    private lateinit var crrStorage: CertificateRevocationRequestStorage
    private lateinit var server: CertificateRevocationSocketServer

    @Before
    fun setUp() {
        crlStorage = mock {
            on { getCertificateRevocationList(eq(CrlIssuer.DOORMAN)) }.then { createCrl() }
        }
        crrStorage = mock {
            on { getRevocationRequests(eq(RequestStatus.APPROVED)) }.then { listOf(createCertificateRevocationRequest(RequestStatus.APPROVED)) }
            on { getRevocationRequests(eq(RequestStatus.DONE)) }.then { listOf(createCertificateRevocationRequest(RequestStatus.DONE)) }
        }
        server = CertificateRevocationSocketServer(freePort(), crlStorage, crrStorage)
        server.start()
        Thread.sleep(100)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `crl is served correctly`() {
        // given

        // when
        val crl = Socket("localhost", server.port).use {
            it.getOutputStream().writeObject(CrlRetrievalMessage())
            it.getInputStream().readObject<CrlResponseMessage>().crl
        }

        // then
        assertNotNull(crl)
    }

    @Test
    fun `approved requests are served correctly`() {
        // given

        // when
        val approvedRequests = Socket("localhost", server.port).use {
            it.getOutputStream().writeObject(CrrsByStatusMessage(RequestStatus.APPROVED))
            it.getInputStream().readObject<List<CertificateRevocationRequestData>>()
        }
        // then
        assertNotNull(approvedRequests)
        assertEquals(RequestStatus.APPROVED, approvedRequests.first().status)
    }

    @Test
    fun `done requests are served correctly`() {
        // given

        // when
        val doneRequests = Socket("localhost", server.port).use {
            it.getOutputStream().writeObject(CrrsByStatusMessage(RequestStatus.DONE))
            it.getInputStream().readObject<List<CertificateRevocationRequestData>>()
        }
        // then
        assertNotNull(doneRequests)
        assertEquals(RequestStatus.DONE, doneRequests.first().status)
    }

    private fun createCrl(): X509CRL {
        val builder = X509v2CRLBuilder(CordaX500Name.build(DEV_INTERMEDIATE_CA.certificate.issuerX500Principal).x500Name, Date())
        val provider = BouncyCastleProvider()
        val crlHolder = builder.build(ContentSignerBuilder.build(Crypto.RSA_SHA256, Crypto.generateKeyPair(Crypto.RSA_SHA256).private, provider))
        return JcaX509CRLConverter().setProvider(provider).getCRL(crlHolder)
    }

    private fun createCertificateRevocationRequest(status: RequestStatus): CertificateRevocationRequestData {
        return CertificateRevocationRequestData(
                requestId = "123",
                legalName = CordaX500Name.parse("CN=Test Corp, O=Test, L=London, C=GB"),
                reporter = "Test",
                certificateSerialNumber = BigInteger.TEN,
                status = status,
                reason = CRLReason.KEY_COMPROMISE,
                modifiedAt = Instant.now(),
                certificateSigningRequestId = "CSR-ID",
                certificate = createDevNodeCa(DEV_INTERMEDIATE_CA, CordaX500Name.parse("O=R3Cev, L=London, C=GB")).certificate
        )
    }
}