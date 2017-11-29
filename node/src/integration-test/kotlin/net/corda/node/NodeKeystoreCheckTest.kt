package net.corda.node

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.cert
import net.corda.core.internal.div
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.*
import net.corda.testing.ALICE_NAME
import net.corda.testing.driver.driver
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

class NodeKeystoreCheckTest {
    @Test
    fun `node should throw exception if cert path doesn't chain to the trust root`() {
        driver(startNodesInProcess = true) {
            // This will fail because there are no keystore configured.
            assertFailsWith(ExecutionException::class) {
                startNode(customOverrides = mapOf("devMode" to false)).get()
            }

            // Create keystores
            val keystorePassword = "password"
            val config = object : SSLConfiguration {
                override val keyStorePassword: String = keystorePassword
                override val trustStorePassword: String = keystorePassword
                override val certificatesDirectory: Path = baseDirectory(ALICE_NAME.toString()) / "certificates"
            }
            config.configureDevKeyAndTrustStores(ALICE_NAME)

            // This should pass with correct keystore.
            val node = startNode(providedName = ALICE_NAME, customOverrides = mapOf("devMode" to false,
                    "keyStorePassword" to keystorePassword,
                    "trustStorePassword" to keystorePassword)).get()
            node.stop()

            // Fiddle with node keystore.
            val keystore = loadKeyStore(config.nodeKeystore, config.keyStorePassword)

            // Self signed root
            val badRootKeyPair = Crypto.generateKeyPair()
            val badRoot = X509Utilities.createSelfSignedCACertificate(CordaX500Name("Bad Root", "Lodnon", "GB"), badRootKeyPair)
            val nodeCA = keystore.getCertificateAndKeyPair(X509Utilities.CORDA_CLIENT_CA, config.keyStorePassword)
            val badNodeCACert = X509Utilities.createCertificate(CertificateType.CLIENT_CA, badRoot, badRootKeyPair, ALICE_NAME, nodeCA.keyPair.public)
            keystore.setKeyEntry(X509Utilities.CORDA_CLIENT_CA, nodeCA.keyPair.private, config.keyStorePassword.toCharArray(), arrayOf(badNodeCACert.cert, badRoot.cert))
            keystore.save(config.nodeKeystore, config.keyStorePassword)

            assertFailsWith(ExecutionException::class) {
                // This will fail because there are no keystore configured.
                startNode(providedName = ALICE_NAME, customOverrides = mapOf("devMode" to false)).get()
            }
        }
    }
}
