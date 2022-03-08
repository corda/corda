package com.r3.conclave.encryptedtx.enclave

import com.github.benmanes.caffeine.cache.Caffeine
import com.r3.conclave.encryptedtx.dto.ConclaveLedgerTxModel
import com.r3.conclave.encryptedtx.dto.VerifiableTxAndDependencies
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.sign
import net.corda.core.transactions.EncryptedTransaction
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory

class EncryptedTxEnclave {

    private val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
    private val signatureMetadata = SignatureMetadata(
            platformVersion = 1,
            schemeNumberID = Crypto.findSignatureScheme(keyPair.public).schemeNumberID
    )

    private val serializerFactoriesForContexts = Caffeine.newBuilder()
            .maximumSize(128)
            .build<SerializationFactoryCacheKey, SerializerFactory>()
            .asMap()

    private val serializationFactoryImpl: SerializationFactoryImpl

    init {
        val serverScheme = AMQPServerSerializationScheme(emptyList(), serializerFactoriesForContexts)
        val clientScheme = AMQPServerSerializationScheme(emptyList(), serializerFactoriesForContexts)

        serializationFactoryImpl = SerializationFactoryImpl().apply {
            registerScheme(serverScheme)
            registerScheme(clientScheme)
        }
    }

    fun encryptSignedTx(ledgerTxBytes: ByteArray): EncryptedTransaction {
        val ledgerTxModel = serializationFactoryImpl.deserialize(
                byteSequence = ByteSequence.of(ledgerTxBytes, 0, ledgerTxBytes.size),
                clazz = ConclaveLedgerTxModel::class.java,
                context = AMQP_P2P_CONTEXT
        )

        val signedTransaction = ledgerTxModel.signedTransaction
        val signableData = SignableData(signedTransaction.id, signatureMetadata)
        val transactionSignature = keyPair.sign(signableData)

        return EncryptedTransaction(
                id = signedTransaction.id,
                encryptedBytes = ledgerTxBytes,
                dependencies = emptySet(),
                sigs = listOf(transactionSignature)
        )
    }

    fun verifyTx(txAndDependenciesBytes: ByteArray) {
        val txAndDependencies = serializationFactoryImpl.deserialize(
                byteSequence = ByteSequence.of(txAndDependenciesBytes, 0, txAndDependenciesBytes.size),
                clazz = VerifiableTxAndDependencies::class.java,
                context = AMQP_P2P_CONTEXT)

        // TODO: add verification
    }
}