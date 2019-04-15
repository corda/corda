package net.corda.node.services.keys.cryptoservice

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.random63BitValue
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.toJca
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.security.auth.x500.X500Principal

/*
 * This class was created with the intention to facilitate the integration of further HSM vendors that provide a JCA provider.
 * For every vendor we want to support there has to be a new VendorCryptoService that implements [CryptoService] and optionally
 * inherits from this class if it makes sense. Not all vendors fully implement the JCA API and some of the methods of this class
 * will have to be overridden with vendor-specific implementations.
 * It is required that @keyStore is initialized.
 */
abstract class JCACryptoService(internal val keyStore: KeyStore, internal val provider: Provider, internal val x500PrincipalForCerts: X500Principal = DUMMY_X500_PRINCIPAL) : CryptoService {

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
        return withAuthentication {
            val keyPairGenerator = keyPairGeneratorFromScheme(scheme)
            val keyPair = keyPairGenerator.generateKeyPair()
            keyStore.setKeyEntry(alias, keyPair.private, null, selfSign(scheme, keyPair))
            keyStore.store(null, null)
            // We call toSupportedKey because it's possible that the PublicKey object returned by the provider is not initialized.
            Crypto.toSupportedPublicKey(keyPair.public)
        }
    }

    override fun containsKey(alias: String): Boolean {
        return withAuthentication {
            keyStore.containsAlias(alias)
        }
    }

    override fun getPublicKey(alias: String): PublicKey? {
        return withAuthentication {
            keyStore.getCertificate(alias)?.publicKey?.let {
                Crypto.toSupportedPublicKey(it)
            }
        }
    }

    /**
     * We _could_ consider doing the digest locally and then signing over the hash remotely with NONEwithALGO as a performance optimization.
     */
    override fun sign(alias: String, data: ByteArray): ByteArray {
        return withAuthentication {
            (keyStore.getKey(alias, null) as PrivateKey?)?.let {
                val algorithm = if (it.algorithm == "RSA") {
                    "SHA256withRSA"
                } else {
                    "SHA256withECDSA"
                }
                val signature = Signature.getInstance(algorithm, provider)
                signature.initSign(it)
                signature.update(data)
                signature.sign()
            } ?: throw CryptoServiceException("No key found for alias $alias")
        }
    }

    override fun getSigner(alias: String): ContentSigner {
        return object : ContentSigner {
            private val publicKey: PublicKey = getPublicKey(alias) ?: throw CryptoServiceException("No key found for alias $alias")
            private val sigAlgID: AlgorithmIdentifier = Crypto.findSignatureScheme(publicKey).signatureOID
            private val baos = ByteArrayOutputStream()
            override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgID
            override fun getOutputStream(): OutputStream = baos
            override fun getSignature(): ByteArray = sign(alias, baos.toByteArray())
        }
    }

    /**
     * Create a self-signed certificate for a given [KeyPair]. This is required for storing key pairs in a [KeyStore].
     * Note that the CertificateBuilder used here is provided by BouncyCastle.
     * This is acceptable because it does not use the BC provider, and  we already introduced a dependency on BouncyCastle with
     * `getSigner`.
     * Vendor-specific subclasses might want to override this to use alternative ways of creating self-signed certificates provided by the vendor.
     */
    internal open fun selfSign(scheme: SignatureScheme, keyPair: KeyPair): Array<out Certificate> {
        val window = X509Utilities.getCertificateValidityWindow(X509Utilities.DEFAULT_VALIDITY_WINDOW.first, X509Utilities.DEFAULT_VALIDITY_WINDOW.second)
        val builder = JcaX509v3CertificateBuilder(x500PrincipalForCerts, BigInteger.valueOf(random63BitValue()), window.first, window.second, x500PrincipalForCerts, keyPair.public)
        val signer = ContentSignerBuilder.build(scheme, keyPair.private, provider)
        return arrayOf(builder.build(signer).toJca())
    }

    /*
     * Create and initialize a [KeyPairGenerator] for the provided [SignatureScheme].
     */
    internal open fun keyPairGeneratorFromScheme(scheme: SignatureScheme): KeyPairGenerator {
        val algorithm = when (scheme) {
            Crypto.ECDSA_SECP256R1_SHA256 -> "ECDSA"
            Crypto.ECDSA_SECP256K1_SHA256 -> "ECDSA"
            Crypto.RSA_SHA256 -> "RSA"
            else -> throw IllegalArgumentException("No algorithm for scheme ID ${scheme.schemeNumberID}")
        }
        val keyPairGenerator = KeyPairGenerator.getInstance(algorithm, provider)
        when (scheme) {
            Crypto.ECDSA_SECP256R1_SHA256 -> keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
            Crypto.ECDSA_SECP256K1_SHA256 -> keyPairGenerator.initialize(ECGenParameterSpec("secp256k1"))
            Crypto.RSA_SHA256 -> keyPairGenerator.initialize(scheme.keySize!!)
            else -> throw IllegalArgumentException("No mapping for scheme ID ${scheme.schemeNumberID}")
        }
        return keyPairGenerator
    }

    internal abstract fun isLoggedIn(): Boolean

    internal abstract fun logIn()

    open fun <T> withAuthentication(block: () -> T): T {
        return if (this.isLoggedIn()) {
            block()
        } else {
            logIn()
            block()
        }
    }

    companion object {
        val DUMMY_X500_PRINCIPAL = X500Principal("CN=DUMMY")
    }
}