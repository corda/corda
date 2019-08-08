package net.corda.bridge.services.crl

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.amqp.crl.CrlServer
import net.corda.nodeapi.internal.amqp.crl.CrlServlet
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.testing.internal.DEV_INTERMEDIATE_CA
import net.corda.testing.internal.DEV_ROOT_CA
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.Security
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrlFetcherTest {

    private val ROOT_CA = DEV_ROOT_CA
    private lateinit var INTERMEDIATE_CA: CertificateAndKeyPair

    private lateinit var server: CrlServer

    private val crlServerHitCount = AtomicInteger(0)

    private val revokedNodeCerts: MutableSet<BigInteger> = mutableSetOf()
    private val revokedIntermediateCerts: MutableSet<BigInteger> = mutableSetOf()

    @Before
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
        revokedNodeCerts.clear()
        server = CrlServer(NetworkHostAndPort("localhost", 0), crlServerHitCount, ROOT_CA, { INTERMEDIATE_CA }, revokedNodeCerts, revokedIntermediateCerts)
        server.start()
        INTERMEDIATE_CA = CertificateAndKeyPair(CrlServlet.replaceCrlDistPointCaCertificate(
                DEV_INTERMEDIATE_CA.certificate,
                CertificateType.INTERMEDIATE_CA,
                ROOT_CA.keyPair,
                "http://${server.hostAndPort}/crl/${CrlServlet.INTERMEDIATE_CRL}"), DEV_INTERMEDIATE_CA.keyPair)
        crlServerHitCount.set(0)
    }

    @After
    fun tearDown() {
        server.close()
        revokedNodeCerts.clear()
        revokedIntermediateCerts.clear()
    }

    @Test
    fun `Sunny day retrieval, no proxy`() {

        val fetcher = CrlFetcher(null)

        val fetchResult = fetcher.fetch(INTERMEDIATE_CA.certificate)
        assertEquals(1, crlServerHitCount.get())
        assertEquals(1, fetchResult.size)
        assertNull(fetchResult.single().revokedCertificates)

        val revokedElements = setOf(BigInteger.ONE, BigInteger.TEN)
        revokedIntermediateCerts.addAll(revokedElements)
        val nonEmptyFetchResult = fetcher.fetch(INTERMEDIATE_CA.certificate)
        assertEquals(2, crlServerHitCount.get())
        assertEquals(1, nonEmptyFetchResult.size)
        val revokedCertificates = nonEmptyFetchResult.single().revokedCertificates
        assertEquals(2, revokedCertificates.size)
        assertEquals(revokedElements, revokedCertificates.map { it.serialNumber }.toSet())
    }
}