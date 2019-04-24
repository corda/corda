package net.corda.nodeapi.internal.provider

import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.io.InputStream
import java.io.OutputStream
import java.security.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.*

class DelegatedKeystore(private val signingService: DelegatedSigningService) : KeyStoreSpi() {
    companion object {
        private val logger = contextLogger()
    }

    private val privateKeys = signingService.aliases().map { alias ->
        val publicKey = signingService.certificate(alias)!!.publicKey
        alias to DelegatedPrivateKey(publicKey.algorithm, publicKey.format) { sigAlgo, data ->
            logger.info("Signing using delegated key : $alias, algo : $sigAlgo")
            signingService.sign(alias, sigAlgo, data)
        }
    }.toMap()

    override fun engineGetKey(alias: String, password: CharArray): Key? = privateKeys[alias]

    override fun engineGetCertificate(alias: String): Certificate? = signingService.certificate(alias)

    override fun engineGetCertificateChain(alias: String): Array<X509Certificate>? = signingService.certificates(alias)?.toTypedArray()

    override fun engineAliases(): Enumeration<String>? = Vector(signingService.aliases()).elements()

    override fun engineContainsAlias(var1: String): Boolean = signingService.aliases().contains(var1)

    override fun engineSize(): Int = signingService.aliases().size

    override fun engineIsKeyEntry(alias: String): Boolean = signingService.aliases().contains(alias)

    override fun engineLoad(param: KeyStore.LoadStoreParameter?) {
        // Insert Delegated signature provider if its not registered with java security.
        val providers = Security.getProviders()
        if (providers.size <= 1 || providers[1] !is DelegatedSignatureProvider) {
            Security.insertProviderAt(DelegatedSignatureProvider(), 1)
        }
    }

    // Read only keystore, write operations are not supported.
    override fun engineSetKeyEntry(var1: String, var2: Key, var3: CharArray, var4: Array<Certificate>?) {
        throw UnsupportedOperationException()
    }

    @Throws(KeyStoreException::class)
    override fun engineSetKeyEntry(var1: String, var2: ByteArray, var3: Array<Certificate>) {
        throw UnsupportedOperationException()
    }

    @Throws(KeyStoreException::class)
    override fun engineSetCertificateEntry(var1: String, var2: Certificate) {
        throw UnsupportedOperationException()
    }

    @Throws(KeyStoreException::class)
    override fun engineDeleteEntry(var1: String) {
        throw UnsupportedOperationException()
    }

    override fun engineIsCertificateEntry(var1: String): Boolean {
        throw UnsupportedOperationException()
    }

    override fun engineGetCertificateAlias(var1: Certificate): String? {
        throw UnsupportedOperationException()
    }

    override fun engineStore(var1: OutputStream, var2: CharArray) {
        throw UnsupportedOperationException()
    }

    override fun engineLoad(var1: InputStream, var2: CharArray?) {
        throw UnsupportedOperationException()
    }

    override fun engineGetCreationDate(var1: String): Date? = throw UnsupportedOperationException()
}
