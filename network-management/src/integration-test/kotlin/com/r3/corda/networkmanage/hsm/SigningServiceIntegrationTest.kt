/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.common.HOST
import com.r3.corda.networkmanage.common.HsmBaseTest
import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.doorman.CertificateRevocationConfig
import com.r3.corda.networkmanage.doorman.DoormanConfig
import com.r3.corda.networkmanage.doorman.NetworkManagementServer
import com.r3.corda.networkmanage.hsm.persistence.ApprovedCertificateRequestData
import com.r3.corda.networkmanage.hsm.persistence.DBSignedCertificateRequestStorage
import com.r3.corda.networkmanage.hsm.persistence.SignedCertificateSigningRequestStorage
import com.r3.corda.networkmanage.hsm.signer.HsmCsrSigner
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.hours
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.NodeRegistrationOption
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.rigorousMock
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import javax.persistence.PersistenceException
import kotlin.concurrent.scheduleAtFixedRate

class SigningServiceIntegrationTest : HsmBaseTest() {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private lateinit var timer: Timer
    private lateinit var rootCaCert: X509Certificate
    private lateinit var intermediateCa: CertificateAndKeyPair

    private lateinit var dbName: String

    @Before
    fun setUp() {
        dbName = random63BitValue().toString()
        timer = Timer()
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        this.intermediateCa = intermediateCa
    }

    @After
    fun tearDown() {
        timer.cancel()
    }

    private fun givenSignerSigningAllRequests(storage: SignedCertificateSigningRequestStorage): HsmCsrSigner {
        // Mock signing logic but keep certificate persistence
        return mock {
            on { sign(any()) }.then {
                val approvedRequests: List<ApprovedCertificateRequestData> = uncheckedCast(it.arguments[0])
                for (approvedRequest in approvedRequests) {
                    JcaPKCS10CertificationRequest(approvedRequest.request).run {
                        val nodeCa = createDevNodeCa(intermediateCa, CordaX500Name.parse(subject.toString()))
                        approvedRequest.certPath = X509Utilities.buildCertPath(nodeCa.certificate, intermediateCa.certificate, rootCaCert)
                    }
                }
                storage.store(approvedRequests, "TEST")
            }
        }
    }

    @Test
    fun `Signing service signs approved CSRs`() {
        //Start doorman server
        NetworkManagementServer(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true)).use { server ->
            server.start(
                    hostAndPort = NetworkHostAndPort(HOST, 0),
                    csrCertPathAndKey = null,
                    doormanConfig = DoormanConfig(approveAll = true, approveInterval = 2.seconds.toMillis(), jira = null),
                    revocationConfig = CertificateRevocationConfig(
                            approveAll = true,
                            jira = null,
                            crlUpdateInterval = 2.hours.toMillis(),
                            crlCacheTimeout = 30.minutes.toMillis(),
                            crlEndpoint = URL("http://test.com/crl"),
                            approveInterval = 10.minutes.toMillis()
                    ),
                    startNetworkMap = null)
            val doormanHostAndPort = server.hostAndPort
            // Start Corda network registration.
            val config = createConfig().also {
                doReturn(ALICE_NAME).whenever(it).myLegalName
                doReturn(URL("http://${doormanHostAndPort.host}:${doormanHostAndPort.port}")).whenever(it).compatibilityZoneURL
            }
            val signingServiceStorage = DBSignedCertificateRequestStorage(configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true)))

            val hsmSigner = givenSignerSigningAllRequests(signingServiceStorage)
            // Poll the database for approved requests
            timer.scheduleAtFixedRate(0, 1.seconds.toMillis()) {
                // The purpose of this tests is to validate the communication between this service and Doorman
                // by the means of data in the shared database.
                // Therefore the HSM interaction logic is mocked here.
                try {
                    val approved = signingServiceStorage.getApprovedRequests()
                    if (approved.isNotEmpty()) {
                        hsmSigner.sign(approved)
                        timer.cancel()
                    }
                } catch (exception: PersistenceException) {
                    // It may happen that Doorman DB is not created at the moment when the signing service polls it.
                    // This is due to the fact that schema is initialized at the time first hibernate session is established.
                    // Since Doorman does this at the time the first CSR arrives, which in turn happens after signing service
                    // startup, the very first iteration of the signing service polling fails with
                    // [org.hibernate.tool.schema.spi.SchemaManagementException] being thrown as the schema is missing.
                }
            }
            config.certificatesDirectory.createDirectories()
            val networkTrustStorePath = config.certificatesDirectory / "network-root-truststore.jks"
            val networkTrustStorePassword = "network-trust-password"
            val networkTrustStore = X509KeyStore.fromFile(networkTrustStorePath, networkTrustStorePassword, createNew = true)
            networkTrustStore.update {
                setCertificate(X509Utilities.CORDA_ROOT_CA, rootCaCert)
            }
            val trustStore = X509KeyStore.fromFile(config.trustStoreFile, config.trustStorePassword, createNew = true)
            val nodeKeyStore = X509KeyStore.fromFile(config.nodeKeystore, config.keyStorePassword, createNew = true)
            val sslKeyStore = X509KeyStore.fromFile(config.sslKeystore, config.keyStorePassword, createNew = true)
            config.also {
                doReturn(trustStore).whenever(it).loadTrustStore(any())
                doReturn(nodeKeyStore).whenever(it).loadNodeKeyStore(any())
                doReturn(sslKeyStore).whenever(it).loadSslKeyStore(any())
            }
            val regConfig = NodeRegistrationOption(networkTrustStorePath, networkTrustStorePassword)
            NetworkRegistrationHelper(config, HTTPNetworkRegistrationService(config.compatibilityZoneURL!!), regConfig).buildKeystore()
            verify(hsmSigner).sign(any())
        }
    }

    private fun createConfig(): NodeConfiguration {
        return rigorousMock<NodeConfiguration>().also {
            doReturn(tempFolder.root.toPath()).whenever(it).baseDirectory
            doReturn(it.baseDirectory / "certificates").whenever(it).certificatesDirectory
            doReturn(it.certificatesDirectory / "truststore.jks").whenever(it).trustStoreFile
            doReturn(it.certificatesDirectory / "nodekeystore.jks").whenever(it).nodeKeystore
            doReturn(it.certificatesDirectory / "sslkeystore.jks").whenever(it).sslKeystore
            doReturn("trustpass").whenever(it).trustStorePassword
            doReturn("cordacadevpass").whenever(it).keyStorePassword
            doReturn("iTest@R3.com").whenever(it).emailAddress
        }
    }
}
