/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.test.cordapp.v1

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.testMessage.MESSAGE_CONTRACT_PROGRAM_ID
import net.corda.testMessage.Message
import net.corda.testMessage.MessageContract
import net.corda.testMessage.MessageState

@StartableByRPC
@InitiatingFlow
class SendMessageFlow(private val message: Message, private val notary: Party, private val reciepent: Party? = null) : FlowLogic<SignedTransaction>() {
    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on the message.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(GENERATING_TRANSACTION, VERIFYING_TRANSACTION, SIGNING_TRANSACTION, FINALISING_TRANSACTION)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GENERATING_TRANSACTION

        val messageState = MessageState(message = message, by = ourIdentity)
        val txCommand = Command(MessageContract.Commands.Send(), messageState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary).withItems(StateAndContract(messageState, MESSAGE_CONTRACT_PROGRAM_ID), txCommand)

        progressTracker.currentStep = VERIFYING_TRANSACTION
        txBuilder.toWireTransaction(serviceHub).toLedgerTransaction(serviceHub).verify()

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = FINALISING_TRANSACTION

        if (reciepent != null) {
            val session = initiateFlow(reciepent)
            subFlow(SendTransactionFlow(session, signedTx))
            return  subFlow(FinalityFlow(signedTx, setOf(reciepent), FINALISING_TRANSACTION.childProgressTracker()))
        } else {
            return subFlow(FinalityFlow(signedTx, FINALISING_TRANSACTION.childProgressTracker()))
        }
    }
}


@InitiatedBy(SendMessageFlow::class)
class Record(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val tx = subFlow(ReceiveTransactionFlow(session, statesToRecord = StatesToRecord.ALL_VISIBLE))
        serviceHub.addSignature(tx)
    }
}