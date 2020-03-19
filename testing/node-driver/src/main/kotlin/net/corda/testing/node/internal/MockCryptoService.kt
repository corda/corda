package net.corda.testing.node.internal

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.internal.Instances
import net.corda.core.crypto.internal.cordaBouncyCastleProvider
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sha256
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.cryptoservice.*
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class MockCryptoService(initialKeyPairs: Map<String, KeyPair>) : CryptoService {

    private val aliasToKey: MutableMap<String, KeyPair> = mutableMapOf()

    private val wrappingKeys: MutableMap<String, SecretKey> = mutableMapOf()

    init {
        initialKeyPairs.forEach {
            aliasToKey[it.key] = it.value
        }
    }

    override fun containsKey(alias: String): Boolean {
        return aliasToKey.containsKey(alias)
    }

    override fun getPublicKey(alias: String): PublicKey? {
        return aliasToKey[alias]?.public
    }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
        try {
            return when (signAlgorithm) {
                null -> Crypto.doSign(aliasToKey[alias]!!.private, data)
                else -> signWithAlgorithm(alias, data, signAlgorithm)
            }
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot sign using the key with alias $alias. SHA256 of data to be signed: ${data.sha256()}", e)
        }
    }

    private fun signWithAlgorithm(alias: String, data: ByteArray, signAlgorithm: String): ByteArray {
        val privateKey = aliasToKey[alias]!!.private
        val signature = Signature.getInstance(signAlgorithm, cordaBouncyCastleProvider)
        signature.initSign(privateKey, newSecureRandom())
        signature.update(data)
        return signature.sign()
    }

    override fun getSigner(alias: String): ContentSigner {
        try {
            val privateKey = aliasToKey[alias]!!.private
            val signatureScheme = Crypto.findSignatureScheme(privateKey)
            return ContentSignerBuilder.build(signatureScheme, privateKey, Crypto.findProvider(signatureScheme.providerName), newSecureRandom())
        } catch (e: Exception) {
            throw CryptoServiceException("Cannot get Signer for key with alias $alias", e)
        }
    }

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
        val keyPair = Crypto.generateKeyPair(scheme)
        aliasToKey[alias] = keyPair
        return keyPair.public
    }

    @Synchronized
    override fun createWrappingKey(alias: String, failIfExists: Boolean) {
        if (wrappingKeys[alias] != null) {
            when (failIfExists) {
                true -> throw IllegalArgumentException("There is an existing key with the alias: $alias")
                false -> return
            }
        }
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(wrappingKeySize())
        val wrappingKey = keyGenerator.generateKey()
        wrappingKeys[alias] = wrappingKey
    }

    override fun generateWrappedKeyPair(masterKeyAlias: String, childKeyScheme: SignatureScheme): Pair<PublicKey, WrappedPrivateKey> {
        val wrappingKey = wrappingKeys[masterKeyAlias] ?: throw IllegalStateException("There is no master key under the alias: $masterKeyAlias")
        val keyPair = Crypto.generateKeyPair(childKeyScheme)
        val cipher = Cipher.getInstance("AES", cordaBouncyCastleProvider)
        cipher.init(Cipher.WRAP_MODE, wrappingKey)
        val privateKeyMaterialWrapped = cipher.wrap(keyPair.private)
        return Pair(keyPair.public, WrappedPrivateKey(privateKeyMaterialWrapped, childKeyScheme))
    }

    override fun sign(masterKeyAlias: String, wrappedPrivateKey: WrappedPrivateKey, payloadToSign: ByteArray): ByteArray {
        val wrappingKey = wrappingKeys[masterKeyAlias] ?: throw IllegalStateException("There is no master key under the alias: $masterKeyAlias")
        val cipher = Cipher.getInstance("AES", cordaBouncyCastleProvider)
        cipher.init(Cipher.UNWRAP_MODE, wrappingKey)
        val privateKey = cipher.unwrap(wrappedPrivateKey.keyMaterial, keyAlgorithmFromScheme(wrappedPrivateKey.signatureScheme), Cipher.PRIVATE_KEY) as PrivateKey
        val signature = Instances.getSignatureInstance(wrappedPrivateKey.signatureScheme.signatureName, cordaBouncyCastleProvider)
        signature.initSign(privateKey, newSecureRandom())
        signature.update(payloadToSign)
        return signature.sign()
    }

    private fun keyAlgorithmFromScheme(scheme: SignatureScheme): String = when (scheme) {
        Crypto.ECDSA_SECP256R1_SHA256, Crypto.ECDSA_SECP256K1_SHA256 -> "EC"
        Crypto.RSA_SHA256 -> "RSA"
        else -> throw IllegalArgumentException("No algorithm for scheme ID ${scheme.schemeNumberID}")
    }

    override fun getWrappingMode(): WrappingMode? {
        return WrappingMode.DEGRADED_WRAPPED
    }
}