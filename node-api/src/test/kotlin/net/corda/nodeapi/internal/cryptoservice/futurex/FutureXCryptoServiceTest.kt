package net.corda.nodeapi.internal.cryptoservice.futurex

import fx.security.pkcs11.SunPKCS11
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceSpec
import net.corda.nodeapi.internal.cryptoservice.WrappingMode
import net.corda.nodeapi.internal.cryptoservice.futurex.FutureXCryptoService.Companion.KEYSTORE_TYPE
import org.junit.Ignore
import java.security.KeyStore

@Ignore
class FutureXCryptoServiceTest: CryptoServiceSpec() {

    override fun delete(alias: String) {
        (getCryptoService() as FutureXCryptoService).delete(alias)
    }

    override fun getCryptoService(): CryptoService {
        val provider = SunPKCS11()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE, provider)
        val config = FutureXCryptoService.FutureXConfiguration("password")
        return FutureXCryptoService(keyStore, provider) { config }
    }

    override fun getSupportedSchemes(): List<SignatureScheme> = listOf(Crypto.RSA_SHA256, Crypto.ECDSA_SECP256R1_SHA256)

    override fun getSupportedSchemesForWrappingOperations(): List<SignatureScheme> = emptyList()

    override fun getSupportedWrappingMode(): WrappingMode? = null

}