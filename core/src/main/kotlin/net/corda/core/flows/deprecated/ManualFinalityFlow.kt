package net.corda.core.flows.deprecated

import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

/**
 * Alternative finality flow which only does not attempt to take participants from the transaction, but instead all
 * participating parties must be provided manually.
 *
 * @param transactions What to commit.
 * @param recipients List of participants to inform of the transaction.
 */
@Deprecated("Use net.corda.core.flows.ManualFinalityFlow")
class ManualFinalityFlow(transactions: Iterable<SignedTransaction>,
                         recipients: Set<Party>,
                         progressTracker: ProgressTracker) : FinalityFlow(transactions, recipients, progressTracker) {
    constructor(transaction: SignedTransaction, extraParticipants: Set<Party>) : this(listOf(transaction), extraParticipants, tracker())
    override fun lookupParties(ltx: LedgerTransaction): Set<Party> = emptySet()
}
