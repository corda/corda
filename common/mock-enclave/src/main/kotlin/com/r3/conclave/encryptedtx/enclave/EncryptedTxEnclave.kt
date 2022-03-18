package com.r3.conclave.encryptedtx.enclave

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.conclave.common.EnclaveClient
import net.corda.core.conclave.common.FlowIdAndPayload
import net.corda.core.conclave.common.LedgerTxHelper
import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.conclave.common.dto.EncryptedVerifiableTxAndDependencies
import net.corda.core.conclave.common.dto.VerifiableTxAndDependencies
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.sign
import net.corda.core.internal.dependencies
import net.corda.core.transactions.EncryptedTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory

class EncryptedTxEnclaveClient() : EnclaveClient {

    // this will be 'our' key to sign over verified transactions
    private val enclaveKeyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
    private val signatureMetadata = SignatureMetadata(
            platformVersion = 1,
            schemeNumberID = Crypto.findSignatureScheme(enclaveKeyPair.public).schemeNumberID
    )

    private val serializerFactoriesForContexts = Caffeine.newBuilder()
            .maximumSize(128)
            .build<SerializationFactoryCacheKey, SerializerFactory>()
            .asMap()

    private val serializationFactoryImpl: SerializationFactoryImpl

    var rejectAttestation = false

    init {
        val serverScheme = AMQPServerSerializationScheme(emptyList(), serializerFactoriesForContexts)
        val clientScheme = AMQPServerSerializationScheme(emptyList(), serializerFactoriesForContexts)

        serializationFactoryImpl = SerializationFactoryImpl().apply {
            registerScheme(serverScheme)
            registerScheme(clientScheme)
        }
    }

    override fun getEnclaveInstanceInfo(): ByteArray {

        // no remote attestations in this remote enclave
        return byteArrayOf()
    }

    override fun registerRemoteEnclaveInstanceInfo(flowIdAndRemoteAttestation: FlowIdAndPayload<ByteArray>) {
        // for testing
        if (rejectAttestation) {
            throw EnclaveClient.RemoteAttestationException("Invalid attestation")
        }
    }

    override fun enclaveVerifyWithoutSignatures(txAndDependencies: VerifiableTxAndDependencies) {
        verifyTx(txAndDependencies, false)
    }

    override fun enclaveVerifyWithSignatures(txAndDependencies: VerifiableTxAndDependencies): EncryptedTransaction {

        verifyTx(txAndDependencies, true)

        val ledgerTx = txAndDependencies.conclaveLedgerTxModel
        val transactionSignature = getSignature(ledgerTx.signedTransaction.id)
        return encrypt(ledgerTx).addSignature(transactionSignature)
    }

    override fun enclaveVerifyWithSignatures(encryptedTxAndDependencies: EncryptedVerifiableTxAndDependencies): EncryptedTransaction {
        val decrypted = decrypt(encryptedTxAndDependencies.encryptedTransaction)

        val verifiableTxAndDependencies = VerifiableTxAndDependencies(
                decrypted,
                encryptedTxAndDependencies.dependencies,
                encryptedTxAndDependencies.encryptedDependencies
        )

        verifyTx(verifiableTxAndDependencies)

        val transactionSignature = getSignature(decrypted.signedTransaction.id)
        return encrypt(decrypted).addSignature(transactionSignature)
    }

    override fun encryptTransactionForLocal(encryptedTransaction: EncryptedTransaction): EncryptedTransaction {
        // no re-encryption in this mock enclave, in a real one we'd need to decrypt from the remote then re-encrypt with whatever key
        // we want to use for long term storage
        return encryptedTransaction
    }

    override fun encryptConclaveLedgerTxForRemote(flowIdWithConclaveLedgerTx: FlowIdAndPayload<ConclaveLedgerTxModel>) : EncryptedTransaction {
        // just serialise in this mock enclave, in a real one we'd need to encrypt for the remote party
        val conclaveLedgerTxModel = flowIdWithConclaveLedgerTx.payload
        return encrypt(conclaveLedgerTxModel)
    }

    override fun encryptEncryptedTransactionForRemote(flowIdWithLocallyEncryptedTx: FlowIdAndPayload<EncryptedTransaction>): EncryptedTransaction {
        // no re-encryption in this mock enclave, in a real one we'd need to decrypt from the remote then re-encrypt with whatever key
        // we want to use for long term storage
        return flowIdWithLocallyEncryptedTx.payload
    }

    private fun getSignature(transactionId : SecureHash) : TransactionSignature {
        val signableData = SignableData(transactionId, signatureMetadata)
        return  enclaveKeyPair.sign(signableData)
    }

    private fun verifyTx(txAndDependencies: VerifiableTxAndDependencies, checkSufficientSignatures: Boolean = true) {

        val signedTransaction = txAndDependencies.conclaveLedgerTxModel.signedTransaction

        if (checkSufficientSignatures) {
            signedTransaction.verifyRequiredSignatures()
        }

        val dependencies = txAndDependencies.dependencies + decryptDependencies(txAndDependencies.encryptedDependencies)

        require(dependencies.map { it.id }.containsAll(signedTransaction.dependencies)) {
            "Missing dependencies to resolve transaction"
        }

        val ledgerTransaction = LedgerTxHelper.toLedgerTxInternal(txAndDependencies.conclaveLedgerTxModel, dependencies)
        ledgerTransaction.verify()
    }

    private fun decryptDependencies(dependencies: Set<EncryptedTransaction>): Set<SignedTransaction> {
        // simply deserialize for this "mock enclave"
        return dependencies.map {
            dependency ->

            // firstly, ensure that WE have signed over this dependency before, else we cannot trust that it has been verified
            val ourSig = dependency.sigs.singleOrNull { it.by == enclaveKeyPair.public }

            ourSig?.let {
                it.verify(dependency.id)
            } ?: throw IllegalStateException("An encrypted dependency was provided ")

            decrypt(dependency).signedTransaction
        }.toSet()
    }

    private fun encrypt(ledgerTx: ConclaveLedgerTxModel): EncryptedTransaction {

        return EncryptedTransaction(
                ledgerTx.signedTransaction.id,
                serializationFactoryImpl.serialize(
                        obj = ledgerTx,
                        context = AMQP_P2P_CONTEXT).bytes,
                ledgerTx.inputStates.map { it.ref.txhash }.toSet(),
                emptyList()
        )
    }

    private fun EncryptedTransaction.addSignature(extraSignature : TransactionSignature) : EncryptedTransaction {
        return  this.copy(sigs = this.sigs + extraSignature)
    }

    private fun decrypt(encryptedTransaction: EncryptedTransaction): ConclaveLedgerTxModel {

        return serializationFactoryImpl.deserialize(
                byteSequence = ByteSequence.of(encryptedTransaction.encryptedBytes, 0, encryptedTransaction.encryptedBytes.size),
                clazz = ConclaveLedgerTxModel::class.java,
                context = AMQP_P2P_CONTEXT)
    }
}
