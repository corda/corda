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

infix fun <T> Obligation.State<T>.`at`(dueBefore: Instant) = copy(template = template.copy(dueBefore = dueBefore))
infix fun <T> Obligation.State<T>.`between`(parties: Pair<Party, PublicKey>) = copy(issuer = parties.first, owner = parties.second)
infix fun <T> Obligation.State<T>.`owned by`(owner: PublicKey) = copy(owner = owner)
infix fun <T> Obligation.State<T>.`issued by`(party: Party) = copy(issuer = party)

// Allows you to write 100.DOLLARS.OBLIGATION
val Issued<Currency>.OBLIGATION_DEF: Obligation.StateTemplate<Currency> get() = Obligation.StateTemplate(nonEmptySetOf(Cash().legalContractReference),
        nonEmptySetOf(this), Instant.parse("2020-01-01T17:00:00Z"))
val Amount<Issued<Currency>>.OBLIGATION: Obligation.State<Currency> get() = Obligation.State(Obligation.Lifecycle.NORMAL, MINI_CORP,
        this.token.OBLIGATION_DEF, this.quantity, NullPublicKey)