package com.r3.corda.doorman

import com.google.common.net.HostAndPort
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.doorman.persistence.CertificationRequestStorage
import com.r3.corda.doorman.persistence.DBCertificateRequestStorage
import com.r3.corda.doorman.persistence.DoormanSchemaService
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.*
import net.corda.node.utilities.*
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.testing.ALICE
import net.corda.testing.testNodeConfiguration
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.util.*
import kotlin.test.assertEquals

class DoormanIntegrationTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `Network Registration With Doorman`() {
        val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(getX500Name(CN = "Integration Test Corda Node Root CA", O = "R3 Ltd", L = "London", C = "GB"), rootCAKey)
        val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey,
                getX500Name(CN = "Integration Test Corda Node Intermediate CA", L = "London", C = "GB", O = "R3 Ltd"), intermediateCAKey.public)

        val database = configureDatabase(makeTestDataSourceProperties(), null, { DoormanSchemaService() }, createIdentityService = {
            // Identity service not needed doorman, corda persistence is not very generic.
            throw UnsupportedOperationException()
        })
        //Start doorman server
        val doorman = DoormanServer(HostAndPort.fromParts("localhost", 0), CertificateAndKeyPair(intermediateCACert, intermediateCAKey), rootCACert.toX509Certificate(),
                object : CertificationRequestStorage by DBCertificateRequestStorage(database) {
                    // The doorman is in approve all mode, returns all pending request id as approved request id.
                    override fun getApprovedRequestIds() = getPendingRequestIds()
                })
        doorman.start()

        // Start Corda network registration.
        val config = testNodeConfiguration(
                baseDirectory = tempFolder.root.toPath(),
                myLegalName = ALICE.name).also {
            whenever(it.certificateSigningService).thenReturn(URL("http://localhost:${doorman.hostAndPort.port}"))
        }

        NetworkRegistrationHelper(config, HTTPNetworkRegistrationService(config.certificateSigningService)).buildKeystore()

        // Checks the keystore are created with the right certificates and keys.
        assert(config.nodeKeystore.toFile().exists())
        assert(config.sslKeystore.toFile().exists())
        assert(config.trustStoreFile.toFile().exists())

        loadKeyStore(config.nodeKeystore, config.keyStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_CLIENT_CA))
            assertEquals(ALICE.name.copy(commonName = X509Utilities.CORDA_CLIENT_CA_CN).x500Name, getX509Certificate(X509Utilities.CORDA_CLIENT_CA).subject)
            assertEquals(listOf(intermediateCACert.cert, rootCACert.cert), getCertificateChain(X509Utilities.CORDA_CLIENT_CA).drop(1).toList())
        }

        loadKeyStore(config.sslKeystore, config.keyStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_CLIENT_TLS))
            assertEquals(ALICE.name.x500Name, getX509Certificate(X509Utilities.CORDA_CLIENT_TLS).subject)
            assertEquals(listOf(intermediateCACert.cert, rootCACert.cert), getCertificateChain(X509Utilities.CORDA_CLIENT_TLS).drop(2).toList())
        }

        loadKeyStore(config.trustStoreFile, config.trustStorePassword).apply {
            assert(containsAlias(X509Utilities.CORDA_ROOT_CA))
            assertEquals(rootCACert.cert.subject, getX509Certificate(X509Utilities.CORDA_ROOT_CA).subject)
        }
        doorman.close()
    }


    private fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
        val props = Properties()
        props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
        props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
        props.setProperty("dataSource.user", "sa")
        props.setProperty("dataSource.password", "")
        return props
    }
}