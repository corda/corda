package net.corda.nodeapi.internal.provider

import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Provider
import java.security.cert.X509Certificate



class DelegatedKeystoreProvider : Provider( PROVIDER_NAME,
        0.1, "JCA/JCE delegated keystore provider") {

    companion object {
        const val PROVIDER_NAME = "DelegatedKeyStore"
    }

    fun putService(name: String, signingService: DelegatedSigningService) {
        putService(DelegatedKeyStoreService(this, name, signingService))
    }

    private class DelegatedKeyStoreService(provider: Provider, name: String, private val signingService: DelegatedSigningService) : Service(provider,
            "KeyStore", name, "DelegatedKeyStore", null, null) {
        @Throws(NoSuchAlgorithmException::class)
        override fun newInstance(var1: Any?): Any {
            return DelegatedKeystore(signingService)
        }
    }
}

class DelegatedPrivateKey(private val algorithm: String, private val format: String, private val signOp: (String, ByteArray) -> ByteArray?) : PrivateKey {
    override fun getFormat() = format
    fun sign(sigAlgo: String, data: ByteArray): ByteArray? = signOp(sigAlgo, data)
    override fun getAlgorithm() = algorithm
    override fun getEncoded(): ByteArray {
        throw UnsupportedOperationException()
    }
}

fun CertificateStore.extractCertificates(): Map<String, List<X509Certificate>> {
    return value.extractCertificates()
}

fun X509KeyStore.extractCertificates(): Map<String, List<X509Certificate>> {
    return aliases().asSequence().map { alias ->
        alias to getCertificateChain(alias)
    }.toMap()
}