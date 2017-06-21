package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.issuedBy
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * Initiates a flow that produces an cash exit transaction.
 *
 * @param amount the amount of a currency to remove from the ledger.
 * @param issuerRef the reference on the issued currency. Added to the node's legal identity to determine the
 * issuer.
 */
@StartableByRPC
class CashExitFlow(val amount: Amount<Currency>, val issueRef: OpaqueBytes, progressTracker: ProgressTracker) : AbstractCashFlow(progressTracker) {
    constructor(amount: Amount<Currency>, issueRef: OpaqueBytes) : this(amount, issueRef, tracker())

    companion object {
        fun tracker() = ProgressTracker(GENERATING_TX, SIGNING_TX, FINALISING_TX)
    }

    @Suspendable
    @Throws(CashException::class)
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GENERATING_TX
        val builder: TransactionBuilder = TransactionType.General.Builder(notary = null as Party?)
        val issuer = serviceHub.myInfo.legalIdentity.ref(issueRef)
        val exitStates = serviceHub.vaultService.unconsumedStatesForSpending<Cash.State>(amount, setOf(issuer.party), builder.notary, builder.lockId, setOf(issuer.reference))
        val signers = try {
            Cash().generateExit(
                    builder,
                    amount.issuedBy(issuer),
                    exitStates)
        } catch (e: InsufficientBalanceException) {
            throw CashException("Exiting more cash than exists", e)
        }

        // Work out who the owners of the burnt states were
        val inputStatesNullable = serviceHub.vaultService.statesForRefs(builder.inputStates())
        val inputStates = inputStatesNullable.values.filterNotNull().map { it.data }
        if (inputStatesNullable.size != inputStates.size) {
            val unresolvedStateRefs = inputStatesNullable.filter { it.value == null }.map { it.key }
            throw IllegalStateException("Failed to resolve input StateRefs: $unresolvedStateRefs")
        }

        // TODO: Is it safe to drop participants we don't know how to contact? Does not knowing how to contact them
        //       count as a reason to fail?
        val participants: Set<Party> = inputStates
                .filterIsInstance<Cash.State>()
                .map { serviceHub.identityService.partyFromAnonymous(it.owner) }
                .filterNotNull()
                .toSet()
        // Sign transaction
        progressTracker.currentStep = SIGNING_TX
        val tx = serviceHub.signInitialTransaction(builder, signers)

        // Commit the transaction
        progressTracker.currentStep = FINALISING_TX
        finaliseTx(participants, tx, "Unable to notarise exit")
        return tx
    }
}
