package net.corda.core.conclave.common.internal

import net.corda.core.conclave.common.dto.ConclaveLedgerTxModel
import net.corda.core.crypto.SecureHash

val ConclaveLedgerTxModel.dependencies: Set<SecureHash>
    get() = (inputStates.asSequence() + references.asSequence()).map { it.ref.txhash }.toSet()