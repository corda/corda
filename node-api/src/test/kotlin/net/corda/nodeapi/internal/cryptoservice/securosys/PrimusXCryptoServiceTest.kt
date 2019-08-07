package net.corda.nodeapi.internal.cryptoservice.securosys

import com.securosys.primus.jce.PrimusProvider
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceSpec
import net.corda.nodeapi.internal.cryptoservice.WrappingMode
import org.junit.Ignore
import java.security.KeyStore

/**
 * This test can run against the cloud HSM provided by Securosys.
 * In order to run it, one has to fill in the appropriate values for the cloud HSM to be used (host, port etc.), available below.
 */
@Ignore
class PrimusXCryptoServiceTest: CryptoServiceSpec() {

    companion object {
        const val TEST_HSM_HOST = "<host>"
        const val TEST_HSM_PORT = 2400
        const val TEST_HSM_USERNAME = "<username>"
        const val TEST_HSM_PASSWORD = "<password>"
    }

    override fun getCryptoService(): CryptoService {
        val provider = PrimusProvider()
        val keyStore = KeyStore.getInstance(PrimusProvider.getKeyStoreTypeName(), provider)
        val auth = { PrimusXCryptoService.Companion.PrimusXConfiguration(TEST_HSM_HOST, TEST_HSM_PORT, TEST_HSM_USERNAME, TEST_HSM_PASSWORD) }

        return PrimusXCryptoService(keyStore, provider, auth = auth)
    }

    override fun delete(alias: String) {
        (getCryptoService() as PrimusXCryptoService).delete(alias)
    }

    override fun getSupportedSchemes(): List<SignatureScheme> = listOf(Crypto.RSA_SHA256, Crypto.ECDSA_SECP256R1_SHA256, Crypto.ECDSA_SECP256K1_SHA256)

    override fun getSupportedSchemesForWrappingOperations(): List<SignatureScheme> = listOf(Crypto.RSA_SHA256, Crypto.ECDSA_SECP256R1_SHA256, Crypto.ECDSA_SECP256K1_SHA256)

    override fun getSupportedWrappingMode(): WrappingMode? = WrappingMode.WRAPPED
}