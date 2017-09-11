package net.corda.core.contracts

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

typealias ContractClassName = String

/**
 * A wrapper for [ContractState] containing additional platform-level state information and contract information.
 * This is the definitive state that is stored on the ledger and used in transaction outputs.
 */
@CordaSerializable
data class TransactionState<out T : ContractState> @JvmOverloads constructor(
        /** The custom contract state */
        val data: T,
        /**
         * An instance of the contract class that will verify this state.
         *
         * # Discussion
         *
         * This field is not the final design, it's just a piece of temporary scaffolding. Once the contract sandbox is
         * further along, this field will become a description of which attachments are acceptable for defining the
         * contract.
         *
         * Recall that an attachment is a zip file that can be referenced from any transaction. The contents of the
         * attachments are merged together and cannot define any overlapping files, thus for any given transaction there
         * is a miniature file system in which each file can be precisely mapped to the defining attachment.
         *
         * Attachments may contain many things (data files, legal documents, etc) but mostly they contain JVM bytecode.
         * The class files inside define not only [Contract] implementations but also the classes that define the states.
         * Within the rest of a transaction, user-providable components are referenced by name only.
         *
         * This means that a smart contract in Corda does two things:
         *
         * 1. Define the data structures that compose the ledger (the states)
         * 2. Define the rules for updating those structures
         *
         * The first is merely a utility role ... in theory contract code could manually parse byte streams by hand.
         * The second is vital to the integrity of the ledger. So this field needs to be able to express constraints like:
         *
         * - Only attachment 733c350f396a727655be1363c06635ba355036bd54a5ed6e594fd0b5d05f42f6 may be used with this state.
         * - Any attachment signed by public key 2d1ce0e330c52b8055258d776c40 may be used with this state.
         * - Attachments (1, 2, 3) may all be used with this state.
         *
         * and so on. In this way it becomes possible for the business logic governing a state to be evolved, if the
         * constraints are flexible enough.
         *
         * Because contract classes often also define utilities that generate relevant transactions, and because attachments
         * cannot know their own hashes, we will have to provide various utilities to assist with obtaining the right
         * code constraints from within the contract code itself.
         *
         * TODO: Implement the above description. See COR-226
         */
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
        val constraint: AttachmentConstraint = AlwaysAcceptAttachmentConstraint)