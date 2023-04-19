package net.corda.nodeapi.internal.revocation

import net.corda.core.crypto.Crypto
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.internal.network.CrlServer
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class CertDistPointCrlSourceTest {
    private lateinit var crlServer: CrlServer

    @Before
    fun setUp() {
        // Do not use Security.addProvider(BouncyCastleProvider()) to avoid EdDSA signature disruption in other tests.
        Crypto.findProvider(BouncyCastleProvider.PROVIDER_NAME)
        crlServer = CrlServer(NetworkHostAndPort("localhost", 0))
        crlServer.start()
    }

    @After
    fun tearDown() {
        if (::crlServer.isInitialized) {
            crlServer.close()
        }
    }

    @Test(timeout=300_000)
	fun `happy path`() {
        val crlSource = CertDistPointCrlSource()

        with(crlSource.fetch(crlServer.intermediateCa.certificate)) {
            assertThat(size).isEqualTo(1)
            assertThat(single().revokedCertificates).isNull()
        }

        val nodeCaCert = crlServer.replaceNodeCertDistPoint(createDevNodeCa(crlServer.intermediateCa, ALICE_NAME).certificate)

        crlServer.revokedNodeCerts += listOf(BigInteger.ONE, BigInteger.TEN)
        with(crlSource.fetch(nodeCaCert)) {  // Use a different cert to avoid the cache
            assertThat(size).isEqualTo(1)
            val revokedCertificates = single().revokedCertificates
            assertThat(revokedCertificates.map { it.serialNumber }).containsExactlyInAnyOrder(BigInteger.ONE, BigInteger.TEN)
        }
    }
}
