package net.corda.core.conclave.common.dto

import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.EncryptedTransaction
import net.corda.core.transactions.SignedTransaction

@CordaSerializable
data class EncryptedVerifiableTxAndDependencies(
        val encryptedTransaction: EncryptedTransaction,
        val dependencies: Set<SignedTransaction>,
        val encryptedDependencies: Set<EncryptedTransaction>
)
