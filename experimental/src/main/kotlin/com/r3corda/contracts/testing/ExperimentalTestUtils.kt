package com.r3corda.contracts.testing

import com.r3corda.contracts.Obligation
import com.r3corda.contracts.cash.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Issued
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.crypto.Party
import com.r3corda.core.testing.MINI_CORP
import com.r3corda.core.utilities.nonEmptySetOf
import java.security.PublicKey
import java.time.Instant
import java.util.*

object JavaExperimental {
    @JvmStatic fun <T> at(state: Obligation.State<T>, dueBefore: Instant) = state.copy(template = state.template.copy(dueBefore = dueBefore))
    @JvmStatic fun <T> between(state: Obligation.State<T>, parties: Pair<Party, PublicKey>) = state.copy(issuer = parties.first, owner = parties.second)
    @JvmStatic fun <T> ownedBy(state: Obligation.State<T>, owner: PublicKey) = state.copy(owner = owner)
    @JvmStatic fun <T> issuedBy(state: Obligation.State<T>, party: Party) = state.copy(issuer = party)

    @JvmStatic fun OBLIGATION_DEF(issued: Issued<Currency>)
        = Obligation.StateTemplate(nonEmptySetOf(Cash().legalContractReference), nonEmptySetOf(issued), Instant.parse("2020-01-01T17:00:00Z"))
    @JvmStatic fun OBLIGATION(amount: Amount<Issued<Currency>>) = Obligation.State(Obligation.Lifecycle.NORMAL, MINI_CORP,
        OBLIGATION_DEF(amount.token), amount.quantity, NullPublicKey)
}
infix fun <T> Obligation.State<T>.`at`(dueBefore: Instant) = JavaExperimental.at(this, dueBefore)
infix fun <T> Obligation.State<T>.`between`(parties: Pair<Party, PublicKey>) = JavaExperimental.between(this, parties)
infix fun <T> Obligation.State<T>.`owned by`(owner: PublicKey) = JavaExperimental.ownedBy(this, owner)
infix fun <T> Obligation.State<T>.`issued by`(party: Party) = JavaExperimental.issuedBy(this, party)

// Allows you to write 100.DOLLARS.OBLIGATION
val Issued<Currency>.OBLIGATION_DEF: Obligation.StateTemplate<Currency> get() = JavaExperimental.OBLIGATION_DEF(this)
val Amount<Issued<Currency>>.OBLIGATION: Obligation.State<Currency> get() = JavaExperimental.OBLIGATION(this)
