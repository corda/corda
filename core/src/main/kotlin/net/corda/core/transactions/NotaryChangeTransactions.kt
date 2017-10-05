package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.serializedHash
import net.corda.core.utilities.toBase58String
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.StateLoader
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

/**
 * A special transaction for changing the notary of a state. It only needs specifying the state(s) as input(s),
 * old and new notaries. Output states can be computed by applying the notary modification to corresponding inputs
 * on the fly.
 */
@CordaSerializable
data class NotaryChangeWireTransaction(
        override val inputs: List<StateRef>,
        override val notary: Party,
        val newNotary: Party
) : CoreTransaction() {
    /**
     * This transaction does not contain any output states, outputs can be obtained by resolving a
     * [NotaryChangeLedgerTransaction] and applying the notary modification to inputs.
     */
    override val outputs: List<TransactionState<ContractState>>
        get() = emptyList()

    init {
        check(inputs.isNotEmpty()) { "A notary change transaction must have inputs" }
        check(notary != newNotary) { "The old and new notaries must be different – $newNotary" }
    }

    /**
     * A privacy salt is not really required in this case, because we already used nonces in normal transactions and
     * thus input state refs will always be unique. Also, filtering doesn't apply on this type of transactions.
     */
    override val id: SecureHash by lazy { serializedHash(inputs + notary + newNotary) }

    fun resolve(services: ServiceHub, sigs: List<TransactionSignature>) = resolve(services as StateLoader, sigs)
    fun resolve(stateLoader: StateLoader, sigs: List<TransactionSignature>): NotaryChangeLedgerTransaction {
        val resolvedInputs = inputs.map { ref ->
            stateLoader.loadState(ref).let { StateAndRef(it, ref) }
        }
        return NotaryChangeLedgerTransaction(resolvedInputs, notary, newNotary, id, sigs)
    }
}

/**
 * A notary change transaction with fully resolved inputs and signatures. In contrast with a regular transaction,
 * signatures are checked against the signers specified by input states' *participants* fields, so full resolution is
 * needed for signature verification.
 */
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
