/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.node.services

import net.corda.core.CordaException
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

/**
 * A service that records input states of the given transaction and provides conflict information
 * if any of the inputs have already been used in another transaction.
 *
 * A uniqueness provider is expected to be used from within the context of a flow.
 */
interface UniquenessProvider {
    /** Commits all input states of the given transaction. */
    fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party, requestSignature: NotarisationRequestSignature)

    /** Specifies the consuming transaction for every conflicting state. */
    @CordaSerializable
    @Deprecated("No longer used due to potential privacy leak")
    @Suppress("DEPRECATION")
    data class Conflict(val stateHistory: Map<StateRef, ConsumingTx>)

    /**
     * Specifies the transaction id, the position of the consumed state in the inputs, and
     * the caller identity requesting the commit.
     */
    @CordaSerializable
    @Deprecated("No longer used")
    data class ConsumingTx(val id: SecureHash, val inputIndex: Int, val requestingParty: Party)
}

@Deprecated("No longer used due to potential privacy leak")
@Suppress("DEPRECATION")
class UniquenessException(val error: UniquenessProvider.Conflict) : CordaException(UniquenessException::class.java.name)