package net.corda.observerdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.observerdemo.contracts.Receivable
import net.corda.observerdemo.contracts.ReceivableContract
import java.time.ZonedDateTime
import java.util.*

@StartableByRPC
class IssueReceivableFlow(private val observer: Party,
                          private val receivableIssueRef: String?,
                          private val receivableCreated: ZonedDateTime,
                          private val accountDebtor: Party,
                          private val receivableValue: Amount<Currency>,
                          private val notary: Party,
                          override val progressTracker: ProgressTracker) : FlowLogic<Receivable>() {
    constructor(observer: Party,
                receivableIssueRef: String?,
                receivableCreated: ZonedDateTime,
                accountDebtor: Party,
                receivableValue: Amount<Currency>,
                notary: Party) : this(observer, receivableIssueRef, receivableCreated, accountDebtor, receivableValue, notary, tracker())

    companion object {
        object GENERATING_TX : ProgressTracker.Step("Generating transaction")
        object SIGNING_TX : ProgressTracker.Step("Signing transaction")
        object FINALISING_TX : ProgressTracker.Step("Finalising transaction")

        fun tracker() = ProgressTracker(GENERATING_TX, SIGNING_TX, FINALISING_TX)
    }

    @Suspendable
    override fun call(): Receivable {
        progressTracker.currentStep = GENERATING_TX
        val builder = TransactionBuilder(notary)
        val signers = ReceivableContract.generateIssue(builder, observer, receivableIssueRef, receivableCreated,
                accountDebtor, receivableValue, ourIdentity, notary)
        progressTracker.currentStep = SIGNING_TX
        var tx = serviceHub.signInitialTransaction(builder, signers)
        progressTracker.currentStep = FINALISING_TX
        tx = subFlow(RegistryObserverFlow.Client(tx))
        return tx.tx.outputs.single().data as Receivable
    }
}