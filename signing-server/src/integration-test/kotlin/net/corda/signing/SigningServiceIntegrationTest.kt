package net.corda.signing

import com.google.common.net.HostAndPort
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.doorman.DoormanServer
import com.r3.corda.doorman.buildCertPath
import com.r3.corda.doorman.persistence.ApprovingAllCertificateRequestStorage
import com.r3.corda.doorman.persistence.DoormanSchemaService
import com.r3.corda.doorman.signer.DefaultCsrHandler
import com.r3.corda.doorman.signer.ExternalSigner
import com.r3.corda.doorman.toX509Certificate
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.seconds
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.signing.hsm.HsmSigner
import net.corda.signing.persistence.ApprovedCertificateRequestData
import net.corda.signing.persistence.DBCertificateRequestStorage
import net.corda.signing.persistence.SigningServerSchemaService
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.CHARLIE
import net.corda.testing.testNodeConfiguration
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.h2.tools.Server
import org.junit.*
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.concurrent.thread
import com.r3.corda.doorman.persistence.DBCertificateRequestStorage.CertificateSigningRequest as DoormanRequest
import net.corda.signing.persistence.DBCertificateRequestStorage.CertificateSigningRequest as SigningServerRequest

class SigningServiceIntegrationTest {

    companion object {
        val H2_TCP_PORT = "8092"
        val HOST = "localhost"
        val DB_NAME = "test_db"
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var timer: Timer

    @Before
    fun setUp() {
        timer = Timer()
    }

    @After
    fun tearDown() {
        timer.cancel()
    }

    private fun givenSignerSigningAllRequests(storage: DBCertificateRequestStorage): HsmSigner {
        // Create all certificates
        val rootCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCACert = X509Utilities.createSelfSignedCACertificate(CordaX500Name(commonName = "Integration Test Corda Node Root CA",
                organisation = "R3 Ltd", locality = "London", country = "GB").x500Name, rootCAKey)
        val intermediateCAKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val intermediateCACert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCACert, rootCAKey,
                CordaX500Name(commonName = "Integration Test Corda Node Intermediate CA", locality = "London", country = "GB",
                        organisation = "R3 Ltd"), intermediateCAKey.public)
        // Mock signing logic but keep certificate persistence
        return mock {
            on { sign(any()) }.then {
                @Suppress("UNCHECKED_CAST")
                val toSign = it.arguments[0] as List<ApprovedCertificateRequestData>
                toSign.forEach {
                    JcaPKCS10CertificationRequest(it.request).run {
                        val certificate = X509Utilities.createCertificate(CertificateType.TLS, intermediateCACert, intermediateCAKey, subject, publicKey).toX509Certificate()
                        it.certPath = buildCertPath(certificate, rootCACert.toX509Certificate())
                    }
                }
                storage.sign(toSign, listOf("TEST"))
            }
        }
    }

    @Test
    fun `Signing service communicates with Doorman`() {
        //Start doorman server
        val doormanStorage = ApprovingAllCertificateRequestStorage(configureDatabase(makeTestDataSourceProperties(), null, { DoormanSchemaService() }, createIdentityService = {
            // Identity service not needed doorman, corda persistence is not very generic.
            throw UnsupportedOperationException()
        }))
        val doorman = DoormanServer(HostAndPort.fromParts(HOST, 0), DefaultCsrHandler(doormanStorage, ExternalSigner()))
        doorman.start()

        // Start Corda network registration.
        val config = testNodeConfiguration(
                baseDirectory = tempFolder.root.toPath(),
                myLegalName = ALICE.name).also {
            whenever(it.certificateSigningService).thenReturn(URL("http://$HOST:${doorman.hostAndPort.port}"))
        }

        val signingServiceStorage = DBCertificateRequestStorage(configureDatabase(makeTestDataSourceProperties(), makeNotInitialisingTestDatabaseProperties(), { SigningServerSchemaService() }, createIdentityService = {
            // Identity service not needed doorman, corda persistence is not very generic.
            throw UnsupportedOperationException()
        }))

        val hsmSigner = givenSignerSigningAllRequests(signingServiceStorage)
        // Poll the database for approved requests
        timer.scheduleAtFixedRate(2.seconds.toMillis(), 1.seconds.toMillis()) {
            // The purpose of this tests is to validate the communication between this service and Doorman
            // by the means of data in the shared database.
            // Therefore the HSM interaction logic is mocked here.
            val approved = signingServiceStorage.getApprovedRequests()
            if (approved.isNotEmpty()) {
                hsmSigner.sign(approved)
                timer.cancel()
            }
        }
        NetworkRegistrationHelper(config, HTTPNetworkRegistrationService(config.certificateSigningService)).buildKeystore()
        verify(hsmSigner).sign(any())
        doorman.close()
    }

    /*
     * Piece of code is purely for demo purposes and should not be considered as actual test (therefore it is ignored).
     * Its purpose is to produce 3 CSRs and wait (polling Doorman) for external signature.
     * The use of the jUnit testing framework was chosen due to the convenience reasons: mocking, tempFolder storage.
     * It is meant to be run together with the [DemoMain.main] method, which executes HSM signing service.
     * The split is done due to the limited console support while executing tests and inability to capture user's input there.
     *
     */
    @Test
    @Ignore
    fun `DEMO - Create CSR and poll`() {
        //Start doorman server
        val doormanStorage = ApprovingAllCertificateRequestStorage(configureDatabase(makeTestDataSourceProperties(), null, { DoormanSchemaService() }, createIdentityService = {
            // Identity service not needed doorman, corda persistence is not very generic.
            throw UnsupportedOperationException()
        }))
        val doorman = DoormanServer(HostAndPort.fromParts(HOST, 0), DefaultCsrHandler(doormanStorage, ExternalSigner()))
        doorman.start()

        thread(start = true, isDaemon = true) {
            val h2ServerArgs = arrayOf("-tcpPort", H2_TCP_PORT, "-tcpAllowOthers")
            Server.createTcpServer(*h2ServerArgs).start()
        }

        // Start Corda network registration.
        (1..3).map {
            thread(start = true) {

                val config = testNodeConfiguration(
                        baseDirectory = tempFolder.root.toPath(),
                        myLegalName = when(it) {
                            1 -> ALICE.name
                            2 -> BOB.name
                            3 -> CHARLIE.name
                            else -> throw IllegalArgumentException("Unrecognised option")
                        }).also {
                    whenever(it.certificateSigningService).thenReturn(URL("http://$HOST:${doorman.hostAndPort.port}"))
                }
                NetworkRegistrationHelper(config, HTTPNetworkRegistrationService(config.certificateSigningService)).buildKeystore()
            }
        }.map { it.join() }
        doorman.close()
    }
}

private fun makeTestDataSourceProperties(): Properties {
    val props = Properties()
    props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    props.setProperty("dataSource.url", "jdbc:h2:mem:${SigningServiceIntegrationTest.DB_NAME};DB_CLOSE_DELAY=-1")
    props.setProperty("dataSource.user", "sa")
    props.setProperty("dataSource.password", "")
    return props
}

internal fun makeNotInitialisingTestDatabaseProperties(): Properties {
    val props = Properties()
    props.setProperty("initDatabase", "false")
    return props
}