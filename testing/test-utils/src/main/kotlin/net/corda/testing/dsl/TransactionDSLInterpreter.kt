/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.dsl

import net.corda.core.DoNotImplement
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.AttachmentConstraint
import net.corda.core.contracts.AutomaticHashConstraint
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.seconds
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

/**
 * This interface defines the bare bone functionality that a Transaction DSL interpreter should implement.
 * @param <R> The return type of [verifies]/[failsWith] and the like. It is generic so that we have control over whether
 * we want to enforce users to call these methods (see [EnforceVerifyOrFail]) or not.
 */
@DoNotImplement
interface TransactionDSLInterpreter : Verifies, OutputStateLookup {
    /**
     * A reference to the enclosing ledger{..}'s interpreter.
     */
    val ledgerInterpreter: LedgerDSLInterpreter<TransactionDSLInterpreter>

    /**
     * Adds an input reference to the transaction. Note that [verifies] will resolve this reference.
     * @param stateRef The input [StateRef].
     */
    fun input(stateRef: StateRef)

    /**
     * Adds an output to the transaction.
     * @param label An optional label that may be later used to retrieve the output probably in other transactions.
     * @param notary The associated notary.
     * @param encumbrance The position of the encumbrance state.
     * @param attachmentConstraint The attachment constraint
     * @param contractState The state itself.
     * @param contractClassName The class name of the contract that verifies this state.
     */
    fun output(contractClassName: ContractClassName,
               label: String?,
               notary: Party,
               encumbrance: Int?,
               attachmentConstraint: AttachmentConstraint,
               contractState: ContractState)

    /**
     * Adds an [Attachment] reference to the transaction.
     * @param attachmentId The hash of the attachment, possibly returned by [LedgerDSLInterpreter.attachment].
     */
    fun attachment(attachmentId: SecureHash)

    /**
     * Adds a command to the transaction.
     * @param signers The signer public keys.
     * @param commandData The contents of the command.
     */
    fun command(signers: List<PublicKey>, commandData: CommandData)

    /**
     * Sets the time-window of the transaction.
     * @param data the [TimeWindow] (validation window).
     */
    fun timeWindow(data: TimeWindow)

    /**
     * Creates a local scoped copy of the transaction.
     * @param dsl The transaction DSL to be interpreted using the copy.
     */
    fun _tweak(dsl: TransactionDSLInterpreter.() -> EnforceVerifyOrFail): EnforceVerifyOrFail

    /**
     * Attaches an attachment containing the named contract to the transaction
     * @param contractClassName The contract class to attach
     */
    fun _attachment(contractClassName: ContractClassName)
}

/**
 * Underlying class for the transaction DSL. Do not instantiate directly, instead use the [transaction] function.
 * */
class TransactionDSL<out T : TransactionDSLInterpreter>(interpreter: T, private val notary: Party) : TransactionDSLInterpreter by interpreter {
    /**
     * Looks up the output label and adds the found state as an input.
     * @param stateLabel The label of the output state specified when calling [TransactionDSLInterpreter.output] and friends.
     */
    fun input(stateLabel: String) = input(retrieveOutputStateAndRef(ContractState::class.java, stateLabel).ref)

    fun input(contractClassName: ContractClassName, stateLabel: String) {
        val stateAndRef = retrieveOutputStateAndRef(ContractState::class.java, stateLabel)
        input(contractClassName, stateAndRef.state.data)
    }

    /**
     * Creates an [LedgerDSLInterpreter._unverifiedTransaction] with a single output state and adds it's reference as an
     * input to the current transaction.
     * @param state The state to be added.
     */
    fun input(contractClassName: ContractClassName, state: ContractState) {
        val transaction = ledgerInterpreter._unverifiedTransaction(null, TransactionBuilder(notary)) {
            output(contractClassName, null, notary, null, AlwaysAcceptAttachmentConstraint, state)
        }
        input(transaction.outRef<ContractState>(0).ref)
    }

    /**
     * Adds a labelled output to the transaction.
     */
    fun output(contractClassName: ContractClassName, label: String, notary: Party, contractState: ContractState) =
            output(contractClassName, label, notary, null, AutomaticHashConstraint, contractState)

    /**
     * Adds a labelled output to the transaction.
     */
    fun output(contractClassName: ContractClassName, label: String, encumbrance: Int, contractState: ContractState) =
            output(contractClassName, label, notary, encumbrance, AutomaticHashConstraint, contractState)

    /**
     * Adds a labelled output to the transaction.
     */
    fun output(contractClassName: ContractClassName, label: String, contractState: ContractState) =
            output(contractClassName, label, notary, null, AutomaticHashConstraint, contractState)

    /**
     * Adds an output to the transaction.
     */
    fun output(contractClassName: ContractClassName, notary: Party, contractState: ContractState) =
            output(contractClassName, null, notary, null, AutomaticHashConstraint, contractState)

    /**
     * Adds an output to the transaction.
     */
    fun output(contractClassName: ContractClassName, encumbrance: Int, contractState: ContractState) =
            output(contractClassName, null, notary, encumbrance, AutomaticHashConstraint, contractState)

    /**
     * Adds an output to the transaction.
     */
    fun output(contractClassName: ContractClassName, contractState: ContractState) =
            output(contractClassName, null, notary, null, AutomaticHashConstraint, contractState)

    /**
     * Adds a command to the transaction.
     */
    fun command(signer: PublicKey, commandData: CommandData) = command(listOf(signer), commandData)

    /**
     * Sets the [TimeWindow] of the transaction.
     * @param time The [Instant] of the [TimeWindow].
     * @param tolerance The tolerance of the [TimeWindow].
     */
    @JvmOverloads
    fun timeWindow(time: Instant, tolerance: Duration = 30.seconds) =
            timeWindow(TimeWindow.withTolerance(time, tolerance))

    /** Creates a local scoped copy of the transaction. */
    fun tweak(dsl: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) =
            _tweak { TransactionDSL(this, notary).dsl() }

    /**
     * @see TransactionDSLInterpreter._attachment
     */
    fun attachment(contractClassName: ContractClassName) = _attachment(contractClassName)

    fun attachments(vararg contractClassNames: ContractClassName) = contractClassNames.forEach { attachment(it) }
}
