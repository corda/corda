package com.r3.corda.sgx.poc

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.lang.IllegalStateException

object TransferFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(val id: Int, val receipient: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val self = serviceHub.myInfo.legalIdentities.first()

            val input = serviceHub.vaultService.queryBy(Asset::class.java).states
                    .firstOrNull { (it.state.data.id == id) && (it.state.data.owner == self) }
                    ?: throw IllegalStateException("Cannot find any unspent asset with id $id")

            val output = input.state.data.copy(owner = receipient)

            val txBuilder = TransactionBuilder(notary)
                    .addInputState(input)
                    .addOutputState(output, AssetContract.ID)
                    .addCommand(AssetContract.Command.Transfer())

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Step 4
            val session = initiateFlow(receipient)
            progressTracker.currentStep = GATHERING_SIGS
            val signedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(session), progressTracker))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            val notarised = subFlow(FinalityFlow(signedTx, setOf(session), progressTracker))
            return notarised
        }
    }

    @InitiatedBy(Initiator::class)
    class Receiver(val session: FlowSession): FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val signTxFlow = object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                }
            }
            val txId = subFlow(signTxFlow).id
            subFlow(ReceiveFinalityFlow(session, txId))
        }
    }
}