package net.corda.node

import net.corda.core.crypto.Crypto
import net.corda.core.internal.div
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import javax.security.auth.x500.X500Principal

class NodeKeystoreCheckTest {
    @Test
    fun `starting node in non-dev mode with no key store`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            assertThatThrownBy {
                startNode(customOverrides = mapOf("devMode" to false)).getOrThrow()
            }.hasMessageContaining("Identity certificate not found")
        }
    }

    @Test
    fun `node should throw exception if cert path doesn't chain to the trust root`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            // Create keystores
            val keystorePassword = "password"
            // TODO sollecitom get rid of NodeSSLConfiguration here. Only nodeKeyStore is used, not the P2P part.
            val config = object : NodeSSLConfiguration {
                override val baseDirectory = Paths.get(".")
                override val keyStorePassword: String = keystorePassword
                override val trustStorePassword: String = keystorePassword
                override val certificatesDirectory: Path = baseDirectory(ALICE_NAME) / "certificates"
            }
            config.configureDevKeyAndTrustStores(ALICE_NAME)

            // This should pass with correct keystore.
            val node = startNode(
                    providedName = ALICE_NAME,
                    customOverrides = mapOf("devMode" to false,
                            "keyStorePassword" to keystorePassword,
                            "trustStorePassword" to keystorePassword)
            ).getOrThrow()
            node.stop()

            // Fiddle with node keystore.
            config.loadNodeKeyStore().update {
                // Self signed root
                val badRootKeyPair = Crypto.generateKeyPair()
                val badRoot = X509Utilities.createSelfSignedCACertificate(X500Principal("O=Bad Root,L=Lodnon,C=GB"), badRootKeyPair)
                val nodeCA = getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA)
                val badNodeCACert = X509Utilities.createCertificate(CertificateType.NODE_CA, badRoot, badRootKeyPair, ALICE_NAME.x500Principal, nodeCA.keyPair.public)
                setPrivateKey(X509Utilities.CORDA_CLIENT_CA, nodeCA.keyPair.private, listOf(badNodeCACert, badRoot))
            }

            assertThatThrownBy {
                startNode(providedName = ALICE_NAME, customOverrides = mapOf("devMode" to false)).getOrThrow()
            }.hasMessage("Client CA certificate must chain to the trusted root.")
        }
    }
}
