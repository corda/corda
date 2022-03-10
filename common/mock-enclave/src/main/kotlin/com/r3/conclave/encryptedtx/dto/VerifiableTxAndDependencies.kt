package com.r3.conclave.encryptedtx.dto

import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.EncryptedTransaction

@CordaSerializable
data class VerifiableTxAndDependencies(
        val conclaveLedgerTxModel: ConclaveLedgerTxModel,
        val dependencies: Set<EncryptedTransaction>
)