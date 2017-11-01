package net.corda.observerdemo.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

/**
 * @param requestDate when the underlying receivable was raised.
 * @param filingDate when the receivable was added to the ledger.
 * @param accountDebtor the party the amount is due to. Often the owner, but not necessarily.
 * @param originatorRef a reference provided by the originator (debtor).
 * @param creditorRef a reference provided by the creditor.
 * @param value the amount the receivable is for.
 */
data class Receivable(override val linearId: UniqueIdentifier = UniqueIdentifier(),
                      val observer: Party,
                      val requestDate: ZonedDateTime,
                      val filingDate: Instant,
                      val accountDebtor: Party,
                      val value: Amount<Currency>,
                      override val owner: AbstractParty) : LinearState, OwnableState, ObservedState {
    companion object {
        fun build(externalId: String?, observer: Party,
                  requestDate: ZonedDateTime, // When the underlying receivable was raised
                  accountDebtor: Party,
                  value: Amount<Currency>,
                  owner: AbstractParty): Receivable {
            return Receivable(UniqueIdentifier(externalId), observer,
                    requestDate, Instant.now(), accountDebtor,
                    value, owner)
        }
    }

    override val observers: List<AbstractParty> = listOf(observer)
    override val participants: List<AbstractParty> = listOf(owner)
    val registeredUtc = filingDate.atZone(ZoneId.of("UTC"))

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState
            = CommandAndState(Commands.Move(null, listOf(Pair(linearId, newOwner))), copy(owner = newOwner))
}
