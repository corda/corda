/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.services.messaging

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.copyTo
import net.corda.core.internal.createDirectories
import net.corda.core.internal.exists
import net.corda.core.internal.x500Name
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration
import org.apache.activemq.artemis.api.core.ActiveMQClusterSecurityException
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.junit.Test
import java.nio.file.Files

/**
 * Runs the security tests with the attacker pretending to be a node on the network.
 */
class MQSecurityAsNodeTest : P2PMQSecurityTest() {
    override fun createAttacker(): SimpleMQClient {
        return clientTo(alice.internals.configuration.p2pAddress)
    }

    override fun startAttacker(attacker: SimpleMQClient) {
        attacker.start(PEER_USER, PEER_USER)  // Login as a peer
    }

    @Test
    fun `send message to RPC requests address`() {
        assertSendAttackFails(RPCApi.RPC_SERVER_QUEUE_NAME)
    }

    @Test
    fun `only the node running the broker can login using the special node user`() {
        val attacker = clientTo(alice.internals.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_USER, NODE_USER)
        }
    }

    @Test
    fun `login as the default cluster user`() {
        val attacker = clientTo(alice.internals.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQClusterSecurityException::class.java).isThrownBy {
            attacker.start(ActiveMQDefaultConfiguration.getDefaultClusterUser(), ActiveMQDefaultConfiguration.getDefaultClusterPassword())
        }
    }

    @Test
    fun `login without a username and password`() {
        val attacker = clientTo(alice.internals.configuration.p2pAddress)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start()
        }
    }

    @Test
    fun `login to a non ssl port as a node user`() {
        val attacker = clientTo(alice.internals.configuration.rpcOptions.address!!, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(NODE_USER, NODE_USER, enableSSL = false)
        }
    }

    @Test
    fun `login to a non ssl port as a peer user`() {
        val attacker = clientTo(alice.internals.configuration.rpcOptions.address!!, sslConfiguration = null)
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER, enableSSL = false)  // Login as a peer
        }
    }

    @Test
    fun `login with invalid certificate chain`() {
        val sslConfig = object : SSLConfiguration {
            override val certificatesDirectory = Files.createTempDirectory("certs")
            override val keyStorePassword: String get() = "cordacadevpass"
            override val trustStorePassword: String get() = "trustpass"

            init {
                val legalName = CordaX500Name("MegaCorp", "London", "GB")
                certificatesDirectory.createDirectories()
                if (!trustStoreFile.exists()) {
                    javaClass.classLoader.getResourceAsStream("certificates/cordatruststore.jks").copyTo(trustStoreFile)
                }

                val clientKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                // Set name constrain to the legal name.
                val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, legalName.x500Name))), arrayOf())
                val clientCACert = X509Utilities.createCertificate(
                        CertificateType.INTERMEDIATE_CA,
                        DEV_INTERMEDIATE_CA.certificate,
                        DEV_INTERMEDIATE_CA.keyPair,
                        legalName.x500Principal,
                        clientKeyPair.public,
                        nameConstraints = nameConstraints)

                val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                // Using different x500 name in the TLS cert which is not allowed in the name constraints.
                val clientTLSCert = X509Utilities.createCertificate(
                        CertificateType.TLS,
                        clientCACert,
                        clientKeyPair,
                        CordaX500Name("MiniCorp", "London", "GB").x500Principal,
                        tlsKeyPair.public)

                loadNodeKeyStore(createNew = true).update {
                    setPrivateKey(
                            X509Utilities.CORDA_CLIENT_CA,
                            clientKeyPair.private,
                            listOf(clientCACert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate))
                }

                loadSslKeyStore(createNew = true).update {
                    setPrivateKey(
                            X509Utilities.CORDA_CLIENT_TLS,
                            tlsKeyPair.private,
                            listOf(clientTLSCert, clientCACert, DEV_INTERMEDIATE_CA.certificate, DEV_ROOT_CA.certificate))
                }
            }
        }

        val attacker = clientTo(alice.internals.configuration.p2pAddress, sslConfig)

        assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
            attacker.start(PEER_USER, PEER_USER)
        }
    }
}
