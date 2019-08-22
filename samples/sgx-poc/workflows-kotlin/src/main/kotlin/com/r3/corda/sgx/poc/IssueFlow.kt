package com.r3.corda.sgx.poc.internal

import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sgx.poc.Asset
import com.r3.corda.sgx.poc.AssetContract
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

@InitiatingFlow
@StartableByRPC
class IssueFlow(val amount: Int) : FlowLogic<SignedTransaction>() {

    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
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

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    override fun call(): SignedTransaction {
        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.
        val secretAsset = Asset(amount, serviceHub.myInfo.legalIdentities.first())
        val txCommand = Command(AssetContract.Command.Issue(), secretAsset.owner.owningKey)
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(secretAsset, AssetContract.ID)
                .addCommand(txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        val notarised = subFlow(FinalityFlow(partSignedTx, emptyList()))

        return notarised
    }
}

