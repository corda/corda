package net.corda.core.contracts

import net.corda.annotations.serialization.Serializable
import net.corda.core.identity.Party

// DOCSTART 1
typealias ContractClassName = String

/**
 * A wrapper for [ContractState] containing additional platform-level state information and contract information.
 * This is the definitive state that is stored on the ledger and used in transaction outputs.
 */
@Serializable
data class TransactionState<out T : ContractState> @JvmOverloads constructor(
        /** The custom contract state */
        val data: T,
        /**
         * The contract class name that will verify this state that will be created via reflection.
         * The attachment containing this class will be automatically added to the transaction at transaction creation
         * time.
         *
         * Currently these are loaded from the classpath of the node which includes the cordapp directory - at some
         * point these will also be loaded and run from the attachment store directly, allowing contracts to be
         * sent across, and run, from the network from within a sandbox environment.
         *
         * TODO: Implement the contract sandbox loading of the contract attachments
         * */
        val contract: ContractClassName,
        /** Identity of the notary that ensures the state is not used as an input to a transaction more than once */
        val notary: Party,
        /**
         * All contract states may be _encumbered_ by up to one other state.
         *
         * The encumbrance state, if present, forces additional controls over the encumbered state, since the platform checks
         * that the encumbrance state is present as an input in the same transaction that consumes the encumbered state, and
         * the contract code and rules of the encumbrance state will also be verified during the execution of the transaction.
         * For example, a cash contract state could be encumbered with a time-lock contract state; the cash state is then only
         * processable in a transaction that verifies that the time specified in the encumbrance time-lock has passed.
         *
         * The encumbered state refers to another by index, and the referred encumbrance state
         * is an output state in a particular position on the same transaction that created the encumbered state. An alternative
         * implementation would be encumbering by reference to a [StateRef], which would allow the specification of encumbrance
         * by a state created in a prior transaction.
         *
         * Note that an encumbered state that is being consumed must have its encumbrance consumed in the same transaction,
         * otherwise the transaction is not valid.
         */
        val encumbrance: Int? = null,
        /**
         * A validator for the contract attachments on the transaction.
         */
        val constraint: AttachmentConstraint = AutomaticHashConstraint)
// DOCEND 1
