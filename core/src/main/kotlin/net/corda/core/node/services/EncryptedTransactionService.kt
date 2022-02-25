package net.corda.core.node.services

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.dependencies
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.EncryptedTransaction
import net.corda.core.transactions.RawDependency
import net.corda.core.transactions.RawDependencyMap
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.SignedTransactionDependencies
import net.corda.core.transactions.SignedTransactionDependencyMap
import net.corda.core.transactions.VerifiedEncryptedTransaction
import net.corda.core.utilities.toHexString
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

// TODO: this should be an interface
class EncryptedTransactionService() : SingletonSerializeAsToken() {
    companion object {

        private const val CRYPTO_TRANSFORMATION = "AES/CBC/PKCS5PADDING"
        private const val CRYPTO_ALGORITHM = "AES"

        private const val IV_BYTE_LENGTH = 16

        // we will use this when we want to sign over an encrypted transaction to attest that we've verified it
        private val enclaveKeyPair = Crypto.generateKeyPair()

        // this key we will use internally for encryption/decryption n.b. this is JUST for a quick PoC, as we will lose this key on restart
        private val encryptionKey = KeyGenerator
                .getInstance(CRYPTO_ALGORITHM)
                .also { it.init(256) }
                .generateKey()

        private fun generateRandomBytes(length: Int): ByteArray {
            val arr = ByteArray(length)
            SecureRandom().nextBytes(arr)
            return arr
        }

        fun getPublicEnclaveKey() = enclaveKeyPair.public
    }

    fun getDependencies(encryptedTransaction: EncryptedTransaction): Set<SecureHash> {

        val stx = decryptTransaction(encryptedTransaction)
        return stx.dependencies
    }

    fun getAttachmentIds(encryptedTransaction: EncryptedTransaction): Set<SecureHash> {

        val stx = decryptTransaction(encryptedTransaction)
        return stx.tx.attachments.toSet()
    }

    fun getNetworkParameterHash(encryptedTransaction: EncryptedTransaction): SecureHash? {

        val stx = decryptTransaction(encryptedTransaction)
        return stx.tx.networkParametersHash
    }

    // TODO: this is glossing over a lot of difficulty here. toLedgerTransaction resolves a lot of stuff via services which wont be available within an enclave
    fun verifyTransaction(encryptedTransaction: EncryptedTransaction, serviceHub : ServiceHub, checkSufficientSignatures: Boolean, rawDependencies: RawDependencyMap): VerifiedEncryptedTransaction {

        println("Verifying encrypted ${encryptedTransaction.id} ${serviceHub.myInfo.legalIdentities.single()}")

        val stx = decryptTransaction(encryptedTransaction)

        val dependencies = extractDependencies(stx.inputs + stx.references, rawDependencies)

        // will throw if cannot verify
        stx.toLedgerTransaction(serviceHub, checkSufficientSignatures, dependencies).verify()

        val sig = Crypto.doSign(enclaveKeyPair.private, stx.txBits.bytes)

        return encryptedTransaction.toVerified(sig)
    }

    fun verifyTransaction(signedTransaction: SignedTransaction, serviceHub: ServiceHub, checkSufficientSignatures: Boolean, rawDependencies: RawDependencyMap) {

        println("Verifying ${signedTransaction.id} ${serviceHub.myInfo.legalIdentities.single()}")

        val dependencies = extractDependencies(signedTransaction.inputs + signedTransaction.references, rawDependencies)

        (signedTransaction.tx.inputsStates + signedTransaction.tx.referenceStates).forEach {
            val dependency = dependencies[it.ref.txhash] ?: throw IllegalArgumentException("Dependency transaction not found")
            val dependencyState = dependency.inputsAndRefs[it.ref] ?: throw IllegalArgumentException("Dependency state not found")

            val dependentState = dependencyState.data
            val suppliedState = it.state.data
            require(dependencyState.data  == it.state.data) {

                "Supplied input/ref on transaction did not match it's stateRef"
            }
        }

        // will throw if cannot verify
        signedTransaction.toLedgerTransaction(serviceHub, checkSufficientSignatures, dependencies).verify()
    }

    fun encryptTransaction(signedTransaction: SignedTransaction): EncryptedTransaction {

        println("Encrypting ${signedTransaction.id} with key: $encryptionKey ${encryptionKey.encoded.toHexString()}")

        val ivBytes = generateRandomBytes(IV_BYTE_LENGTH)
        val initialisationVector = IvParameterSpec(ivBytes)
        val encryptionCipher = Cipher
                .getInstance(CRYPTO_TRANSFORMATION)
                .also { it.init(Cipher.ENCRYPT_MODE, encryptionKey, initialisationVector) }

        val encryptedTxBytes = encryptionCipher.doFinal(ivBytes + signedTransaction.serialize().bytes)
        return EncryptedTransaction(signedTransaction.id, encryptedTxBytes)
    }

    private fun decryptTransaction(encryptedTransaction: EncryptedTransaction): SignedTransaction {

        val encryptedBytes = encryptedTransaction.bytes

        // first IV_BYTE_LENGTH bytes are the IV
        val initialisationVector = IvParameterSpec(encryptedTransaction.bytes.copyOf(IV_BYTE_LENGTH))

        // remainder is the transaction
        val encryptedTransactionBytes = encryptedTransaction.bytes.copyOfRange(IV_BYTE_LENGTH, encryptedBytes.size)

        val decryptionCipher = Cipher
                .getInstance(CRYPTO_TRANSFORMATION)
                .also { it.init(Cipher.DECRYPT_MODE, encryptionKey, initialisationVector) }

        println("Decrypting ${encryptedTransaction.id} with key: $encryptionKey ${encryptionKey.encoded.toHexString()}")

        return decryptionCipher.doFinal(encryptedTransactionBytes).deserialize()
    }

    private fun extractDependencies(requiredStateRefs: List<StateRef>, rawDependencies: RawDependencyMap) : SignedTransactionDependencyMap {

        val requiredStates = requiredStateRefs.map { it.txhash }.distinct()

        val dependencies = requiredStateRefs.map {
            val rawDependency = rawDependencies[it.txhash] ?: throw IllegalArgumentException("Missing raw dependency for ${it.txhash}")
            val tx = rawDependency.getTransaction() ?: throw IllegalArgumentException("Missing raw dependency data for ${it.txhash} $rawDependency")
            it to tx.coreTransaction.outputs[it.index]
        }.groupBy { it.first.txhash }


        return requiredStates.map {
            val resolvedDependencies = dependencies[it] ?: throw IllegalArgumentException("Missing encrypted transaction resolved reference for $it")
            it to  SignedTransactionDependencies(
                    inputsAndRefs = resolvedDependencies.toMap(),
                    networkParameters = rawDependencies[it]?.networkParameters
            )
        }.toMap()
    }

    private fun RawDependency.getTransaction() : SignedTransaction? {
        return signedTransaction ?: encryptedTransaction?.let { decryptTransaction(it) }
    }
}