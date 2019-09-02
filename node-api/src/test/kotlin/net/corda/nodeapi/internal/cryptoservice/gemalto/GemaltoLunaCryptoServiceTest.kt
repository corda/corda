package net.corda.nodeapi.internal.cryptoservice.gemalto

import com.safenetinc.luna.provider.LunaProvider
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.identity.Party
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceSpec
import net.corda.nodeapi.internal.cryptoservice.WrappingMode
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import org.junit.Ignore
import org.junit.Test
import java.security.KeyStore
import java.time.Duration
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Gemalto does not provide a simulator, which means that this test has to be run against
 * one of their cloud HSMs or the box they loaned to us. The latter is not accessible
 * from TeamCity, so the only option would be the cloud HSM. I currently don't know
 * the credentials to that, so it's not an option. Also, running automated tests against
 * their cloud HSMs might turn out to be a bit flaky. For this reason, this test is not
 * enabled. To run it locally, you need to set up the Gemalto JCA provider on your machine.
 * If you don't want to or can't install the client and JCA provider system-wide, you can
 * provide the location of your Chrystoki.conf via an environment variable:
 *     `ChrystokiConfigurationPath=/path/to/your/config`,
 * and the location of the libChrystoki2 via the java library path
 *    `-Djava.library.path=/your/jsp/lib`.
 *
 */
@Ignore
class GemaltoLunaCryptoServiceTest: CryptoServiceSpec() {

    override fun getCryptoService(): CryptoService {
        return GemaltoLunaCryptoService(keyStore, provider) { config }
    }

    override fun delete(alias: String) {
        (getCryptoService() as GemaltoLunaCryptoService).delete(alias)
    }

    override fun getSupportedSchemes(): List<SignatureScheme> = listOf(Crypto.RSA_SHA256, Crypto.ECDSA_SECP256R1_SHA256, Crypto.ECDSA_SECP256K1_SHA256)

    override fun getSupportedSchemesForWrappingOperations(): List<SignatureScheme> = emptyList()

    override fun getSupportedWrappingMode(): WrappingMode? = null

    private val provider = LunaProvider.getInstance()
    private val keyStore = KeyStore.getInstance(GemaltoLunaCryptoService.KEYSTORE_TYPE, provider)

    private val config = GemaltoLunaCryptoService.GemaltoLunaConfiguration("tokenlabel:somepartition", "somepassword")

}
