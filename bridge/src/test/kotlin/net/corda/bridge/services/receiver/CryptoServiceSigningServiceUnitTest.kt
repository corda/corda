package net.corda.bridge.services.receiver

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import com.r3.ha.utilities.InternalTunnelKeystoreGenerator
import net.corda.bridge.services.config.BridgeConfigHelper
import net.corda.cliutils.CommonCliConstants
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.nodeapi.internal.config.*
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.getCertificateAndKeyPair
import net.corda.nodeapi.internal.cryptoservice.*
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.testing.common.internal.isInstanceOf
import net.corda.testing.internal.participant
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import rx.subjects.BehaviorSubject
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.SynchronousQueue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoServiceSigningServiceUnitTest {

    companion object {
        const val existingAlias = "${X509Utilities.CORDA_CLIENT_TLS}float"

        private fun checkKeystore(keyStoreFile: Path, alias: String, keyStorePassword: String, entryPassword: String) {
            assertTrue(keyStoreFile.exists())
            assertTrue(X509KeyStore.fromFile(keyStoreFile, keyStorePassword).contains(alias))
            assertTrue(X509KeyStore.fromFile(keyStoreFile, keyStorePassword).internal.isKeyEntry(alias))
            // The dummy private key in local file is the alias - check this.
            val certificateAndKeyPair = X509KeyStore.fromFile(keyStoreFile, keyStorePassword).internal.getCertificateAndKeyPair(alias, entryPassword)
            val privateKeyFromFile = certificateAndKeyPair.keyPair.private
            assertNotNull(privateKeyFromFile)
        }
    }

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var floatKeystore: FileBasedCertificateStoreSupplier
    private val entryPassword = "entryPassword"

    private lateinit var trustStore: FileBasedCertificateStoreSupplier

    private lateinit var bouncyCryptoService: BCCryptoService

    private lateinit var dummyLegalName: CordaX500Name

    private val sslConfiguration = object : MutualSslConfiguration {
        override val useOpenSsl: Boolean
            get() = false

        override val keyStore: FileBasedCertificateStoreSupplier
            get() = floatKeystore
        override val trustStore: FileBasedCertificateStoreSupplier
            get() = this@CryptoServiceSigningServiceUnitTest.trustStore
    }

    @Before
    fun setup() {
        val generator = InternalTunnelKeystoreGenerator()
        val workingDirectory = tempFolder.root.toPath()
        val keyStorePassword = "keyStorePassword"

        val trustStorePassword = "trustStorePassword"
        CommandLine.populateCommand(generator,
                CommonCliConstants.BASE_DIR, workingDirectory.toString(),
                "--keyStorePassword", keyStorePassword,
                "--entryPassword", entryPassword,
                "--trustStorePassword", trustStorePassword)
        generator.runProgram()

        val floatKeystorePath = workingDirectory / "tunnel" / "float.jks"

        checkKeystore(floatKeystorePath, existingAlias, keyStorePassword, entryPassword)

        floatKeystore = FileBasedCertificateStoreSupplier(floatKeystorePath, keyStorePassword, entryPassword)

        val trustStorePath = workingDirectory / "tunnel" / "tunnel-truststore.jks"
        trustStore = FileBasedCertificateStoreSupplier(trustStorePath, trustStorePassword, trustStorePassword)

        dummyLegalName = CordaX500Name("aa", "bb", "cc", "GB")
        bouncyCryptoService = BCCryptoService(dummyLegalName.x500Principal, object : CertificateStoreSupplier {
            override fun get(createNew: Boolean): CertificateStore = floatKeystore.get()
        })
    }

    @Test
    fun testNormalStart() {

        val instance = CryptoServiceSigningService(null, dummyLegalName, sslConfiguration, null,
                "testNormalStart", {}, { bouncyCryptoService })
        instance.start()
        // Also test ability to sign
        assertNotNull(instance.sign(existingAlias, bouncyCryptoService.defaultTLSSignatureScheme().signatureName, "testPhrase".toByteArray()))
        instance.stop()
    }

    @Test
    fun testUnmappedAliasedStart() {

        // Amend store with root CA for which we obviously will not have a valid private key
        with(Pair(floatKeystore.get(), "foo")) {
            val rootCa = trustStore.get()[CORDA_ROOT_CA]
            first[second] = rootCa
            first.update { setPrivateKey(second, Crypto.generateKeyPair().private, listOf(rootCa), entryPassword) }
        }

        val instance = CryptoServiceSigningService(null, dummyLegalName, sslConfiguration, null,
                "testUnmappedAliasedStart", {}, { bouncyCryptoService })
        Assertions.assertThatThrownBy { instance.start() }.isInstanceOf<CryptoServiceException>()
    }

    private enum class Operation { CONNECT, HEARTBEAT }
    /**
     * Allows to mock sleep() calls in CryptoServiceSigningService execution threads, check next performed operations and, also,
     * simulate failures on operations.
     */
    private class CryptoServiceMock(private val makeCryptoService: () -> CryptoService) {
        private val sleepQueue = SynchronousQueue<Long>()
        private val operationChange = BehaviorSubject.create<Operation>()
        private val operationFollower = operationChange.serialize().toBlocking().iterator
        var failHeartbeat = false
        var failConnect = false

        /**
         * Calls makeCryptoService() inside it.
         */
        fun connect(): CryptoService {
            try {
                if (failConnect) throw IllegalArgumentException("Connect failed")
                val cryptoService = makeCryptoService()
                return object : CryptoService by cryptoService {
                    override fun containsKey(alias: String): Boolean {
                        try {
                            if (failHeartbeat) throw IllegalArgumentException("Heartbeat failed")
                            return cryptoService.containsKey(alias)
                        } finally {
                            operationChange.onNext(Operation.HEARTBEAT)
                        }
                    }
                }
            } finally {
                operationChange.onNext(Operation.CONNECT)
            }
        }

        /**
         * Blocks execution thread until nextOperation(true) is called
         */
        fun sleep(millis: Long) = sleepQueue.put(millis)

        /**
         * Returns the next operation
         * @param wakeUpFromSleep Set true to "wake up" from sleep and release blocked execution thread
         */
        fun nextOperation(wakeUpFromSleep: Boolean = false): Operation {
            if (wakeUpFromSleep) sleepQueue.take()
            return operationFollower.next()
        }
    }

    @Test
    fun testLifecycleWithBCCryptoService() {
        // create signing service
        val mock = CryptoServiceMock { CryptoServiceFactory.makeCryptoService(null, dummyLegalName, sslConfiguration.keyStore) }
        val signingService = CryptoServiceSigningService(null, dummyLegalName, sslConfiguration, 10000,
                name = "Test", sleep = mock::sleep, makeCryptoService = mock::connect)
        val stateFollower = signingService.activeChange.toBlocking().iterator
        assertEquals(false, stateFollower.next())
        assertEquals(false, signingService.active)

        // start signing service and receive 1st heartbeat
        signingService.start()
        assertEquals(true, stateFollower.next())
        assertEquals(true, signingService.active)
        assertEquals(Operation.CONNECT, mock.nextOperation())
        assertEquals(Operation.HEARTBEAT, mock.nextOperation())

        // 2nd heartbeat OK
        assertEquals(Operation.HEARTBEAT, mock.nextOperation(true))

        // 3rd heartbeat failed: signing service disconnected
        mock.failConnect = true
        mock.failHeartbeat = true
        assertEquals(Operation.HEARTBEAT, mock.nextOperation(true))
        assertEquals(false, stateFollower.next())
        assertEquals(false, signingService.active)

        // unable to connect to crypto service
        assertEquals(Operation.CONNECT, mock.nextOperation(true))
        assertEquals(false, signingService.active)

        // crypto service is connected but 1st heartbeat still fails
        mock.failConnect = false
        assertEquals(Operation.CONNECT, mock.nextOperation(true))
        assertEquals(Operation.HEARTBEAT, mock.nextOperation())
        assertEquals(false, signingService.active)

        // signing service is online
        mock.failHeartbeat = false
        assertEquals(Operation.CONNECT, mock.nextOperation(true))
        assertEquals(Operation.HEARTBEAT, mock.nextOperation())
        assertEquals(true, stateFollower.next())
        assertEquals(true, signingService.active)

        // restart service and receive 2 heartbeats
        signingService.stop()
        signingService.start()
        assertEquals(Operation.CONNECT, mock.nextOperation())
        assertEquals(Operation.HEARTBEAT, mock.nextOperation())
        assertEquals(Operation.HEARTBEAT, mock.nextOperation(true))
    }

    /**
     * Only for manual testing of HSM connection. Not intended for automatic run. Must be terminated manually.
     * Two test scenarios are suggested:
     * 1) Start test when HSM connection is active, then terminate HSM connection (e.g. by blocking the port) and restore it again after some time.
     * 2) Start test when HSM connection is blocked, then unblock it.
     *
     * In both cases crypto signing service must be correctly activated after HSM connection is unblocked.
     */
    private fun testLifecycleWithLiveHSM(csName: SupportedCryptoServices, csConfigText: String) {
        // mock certificate store to be able to generate keys and certificates after first connect to HSM
        val alias = UUID.randomUUID().toString()
        val aliases: Enumeration<String> = Collections.enumeration(listOf(alias))
        val certList = mutableListOf<X509Certificate>()
        val x509KeyStore = participant<X509KeyStore>().also {
            doReturn(certList).whenever(it).getCertificateChain(any())
            doReturn(aliases.iterator()).whenever(it).aliases()
        }
        val certificateStore = CertificateStore.of(x509KeyStore, "", "")
        floatKeystore = participant<FileBasedCertificateStoreSupplier>().also {
            doReturn(certificateStore).whenever(it).get(any())
        }

        // generate config file for provided configuration
        val csConfigPath = tempFolder.root.toPath() / "hsm.conf"
        csConfigPath.toFile().writeText(csConfigText)
        val csConfig = object : CryptoServiceConfig {
            override val name = csName
            override val conf = csConfigPath
        }

        // generate keys in HSM and add corresponding certificate to the signing service after first connect to HSM
        val makeCryptoService = {
            val cryptoService = CryptoServiceFactory.makeCryptoService(csConfig, dummyLegalName, sslConfiguration.keyStore)
            println("CryptoService created")
            if (certList.isEmpty()) {
                println("Creating certificate ...")
                val pubKey = cryptoService.generateKeyPair(alias, cryptoService.defaultIdentitySignatureScheme())
                val cert = participant<X509Certificate>().also {
                    doReturn(pubKey).whenever(it).publicKey
                }
                certList.add(cert)
                println("Certificate created OK")
            }
            cryptoService
        }

        // create signing service
        val signingService = CryptoServiceSigningService(csConfig, dummyLegalName, sslConfiguration, 10000,
                name = "Tunnel", sleep = {
            println("Sleeping 1 sec ...")
            Thread.sleep(1000)
        }, makeCryptoService = makeCryptoService)

        // start signing service
        signingService.start()

        // sleep forever: test must be explicitly terminated
        while (true) Thread.sleep(1000)
    }

    /**
     * Only for manual run. See [testLifecycleWithLiveHSM] for description.
     * Requires live connect to Gemalto HSM server, see [net.corda.nodeapi.internal.cryptoservice.gemalto.GemaltoLunaCryptoServiceTest].
     * Edit below configuration to use proper credentials.
     */
    @Ignore
    @Test
    fun testLifecycleWithGemalto() {
        testLifecycleWithLiveHSM(SupportedCryptoServices.GEMALTO_LUNA, """
            keyStore: "tokenlabel:somepartition"
            password: "somepassword"
        """.trimIndent())
    }
}