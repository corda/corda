package net.corda.bridge.services.receiver

import com.r3.ha.utilities.InternalTunnelKeystoreGenerator
import net.corda.bridge.services.TestAuditService
import net.corda.cliutils.CommonCliConstants
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.config.CertificateStoreSupplier
import net.corda.nodeapi.internal.config.FileBasedCertificateStoreSupplier
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.crypto.getCertificateAndKeyPair
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.nodeapi.internal.cryptoservice.bouncycastle.BCCryptoService
import net.corda.testing.common.internal.isInstanceOf
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import picocli.CommandLine
import java.nio.file.Path
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

        val instance = CryptoServiceSigningService(null, dummyLegalName, sslConfiguration, null, TestAuditService(),
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

        val instance = CryptoServiceSigningService(null, dummyLegalName, sslConfiguration, null, TestAuditService(),
                "testUnmappedAliasedStart", {}, { bouncyCryptoService })
        Assertions.assertThatThrownBy { instance.start() }.isInstanceOf<CryptoServiceException>()
    }
}