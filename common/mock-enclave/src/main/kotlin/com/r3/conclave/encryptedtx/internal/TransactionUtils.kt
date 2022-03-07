package com.r3.conclave.encryptedtx.internal

import com.r3.conclave.encryptedtx.dto.ConclaveLedgerTxModel
import net.corda.core.crypto.SecureHash

val ConclaveLedgerTxModel.dependencies: Set<SecureHash>
    get() = (inputStates.asSequence() + references.asSequence()).map { it.ref.txhash }.toSet()