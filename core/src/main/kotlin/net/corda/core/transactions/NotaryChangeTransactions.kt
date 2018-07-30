/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.transactions

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.NotaryChangeWireTransaction.Component.*
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toBase58String
import java.security.PublicKey

/**
 * A special transaction for changing the notary of a state. It only needs specifying the state(s) as input(s),
 * old and new notaries. Output states can be computed by applying the notary modification to corresponding inputs
 * on the fly.
 */
@CordaSerializable
@KeepForDJVM
data class NotaryChangeWireTransaction(
        /**
         * Contains all of the transaction components in serialized form.
         * This is used for calculating the transaction id in a deterministic fashion, since re-serializing properties
         * may result in a different byte sequence depending on the serialization context.
         */
        val serializedComponents: List<OpaqueBytes>
) : CoreTransaction() {
    override val inputs: List<StateRef> = serializedComponents[INPUTS.ordinal].deserialize()
    override val references: List<StateRef> = emptyList()
    override val notary: Party = serializedComponents[NOTARY.ordinal].deserialize()
    /** Identity of the notary service to reassign the states to.*/
    val newNotary: Party = serializedComponents[NEW_NOTARY.ordinal].deserialize()

    /**
     * This transaction does not contain any output states, outputs can be obtained by resolving a
     * [NotaryChangeLedgerTransaction] and applying the notary modification to inputs.
     */
    override val outputs: List<TransactionState<ContractState>>
        get() = throw UnsupportedOperationException("NotaryChangeWireTransaction does not contain output states, " +
                "outputs can only be obtained from a resolved NotaryChangeLedgerTransaction")

    init {
        check(inputs.isNotEmpty()) { "A notary change transaction must have inputs" }
        check(notary != newNotary) { "The old and new notaries must be different – $newNotary" }
        checkBaseInvariants()
    }

    /**
     * A privacy salt is not really required in this case, because we already used nonces in normal transactions and
     * thus input state refs will always be unique. Also, filtering doesn't apply on this type of transactions.
     */
    override val id: SecureHash by lazy {
        serializedComponents.map { component ->
            component.bytes.sha256()
        }.reduce { combinedHash, componentHash ->
            combinedHash.hashConcat(componentHash)
        }
    }

    /** Resolves input states and builds a [NotaryChangeLedgerTransaction]. */
    @DeleteForDJVM
    fun resolve(services: ServicesForResolution, sigs: List<TransactionSignature>): NotaryChangeLedgerTransaction {
        val resolvedInputs = services.loadStates(inputs.toSet()).toList()
        return NotaryChangeLedgerTransaction(resolvedInputs, notary, newNotary, id, sigs)
    }

    /** Resolves input states and builds a [NotaryChangeLedgerTransaction]. */
    @DeleteForDJVM
    fun resolve(services: ServiceHub, sigs: List<TransactionSignature>) = resolve(services as ServicesForResolution, sigs)

    enum class Component {
        INPUTS, NOTARY, NEW_NOTARY
    }

    @Deprecated("Required only for backwards compatibility purposes. This type of transaction should not be constructed outside Corda code.", ReplaceWith("NotaryChangeTransactionBuilder"), DeprecationLevel.WARNING)
    constructor(inputs: List<StateRef>, notary: Party, newNotary: Party) : this(listOf(inputs, notary, newNotary).map { it.serialize() })
}

/**
 * A notary change transaction with fully resolved inputs and signatures. In contrast with a regular transaction,
 * signatures are checked against the signers specified by input states' *participants* fields, so full resolution is
 * needed for signature verification.
 */
@KeepForDJVM
data class NotaryChangeLedgerTransaction(
        override val inputs: List<StateAndRef<ContractState>>,
        override val notary: Party,
        val newNotary: Party,
        override val id: SecureHash,
        override val sigs: List<TransactionSignature>
) : FullTransaction(), TransactionWithSignatures {
    init {
        checkEncumbrances()
    }

    override val references: List<StateAndRef<ContractState>> = emptyList()

    /** We compute the outputs on demand by applying the notary field modification to the inputs */
    override val outputs: List<TransactionState<ContractState>>
        get() = inputs.mapIndexed { pos, (state) ->
            if (state.encumbrance != null) {
                state.copy(notary = newNotary, encumbrance = pos + 1)
            } else state.copy(notary = newNotary)
        }

    override val requiredSigningKeys: Set<PublicKey>
        get() = inputs.flatMap { it.state.data.participants }.map { it.owningKey }.toSet() + notary.owningKey

    override fun getKeyDescriptions(keys: Set<PublicKey>): List<String> {
        return keys.map { it.toBase58String() }
    }

    /**
     * Check that encumbrances have been included in the inputs. The [NotaryChangeFlow] guarantees that an encumbrance
     * will follow its encumbered state in the inputs.
     */
    private fun checkEncumbrances() {
        inputs.forEachIndexed { i, (state, ref) ->
            state.encumbrance?.let {
                val nextIndex = i + 1
                fun nextStateIsEncumbrance() = (inputs[nextIndex].ref.txhash == ref.txhash) && (inputs[nextIndex].ref.index == it)
                if (nextIndex >= inputs.size || !nextStateIsEncumbrance()) {
                    throw TransactionVerificationException.TransactionMissingEncumbranceException(
                            id,
                            it,
                            TransactionVerificationException.Direction.INPUT)
                }
            }
        }
    }
}
