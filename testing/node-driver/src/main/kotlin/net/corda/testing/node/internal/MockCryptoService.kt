package net.corda.testing.node.internal

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.internal.cordaBouncyCastleProvider
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.sha256
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PublicKey
import java.security.Signature

class MockCryptoService(initialKeyPairs: Map<String, KeyPair>) : CryptoService {

    private val aliasToKey: MutableMap<String, KeyPair> = mutableMapOf()

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
            return when(signAlgorithm) {
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
}