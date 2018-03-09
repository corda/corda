/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node

import net.corda.core.crypto.Crypto
import net.corda.core.internal.div
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Path
import javax.security.auth.x500.X500Principal

class NodeKeystoreCheckTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())
    }

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
            val config = object : SSLConfiguration {
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
