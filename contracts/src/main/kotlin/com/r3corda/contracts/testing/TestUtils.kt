package com.r3corda.contracts.testing

import com.r3corda.contracts.*
import com.r3corda.contracts.cash.CASH_PROGRAM_ID
import com.r3corda.contracts.cash.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Contract
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.DUMMY_PROGRAM_ID
import com.r3corda.core.contracts.DummyContract
import com.r3corda.core.contracts.DummyState
import com.r3corda.core.contracts.PartyAndReference
import com.r3corda.core.contracts.Issued
import com.r3corda.core.contracts.TransactionState
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.testing.MINI_CORP
import com.r3corda.core.testing.TEST_TX_TIME
import com.r3corda.core.utilities.nonEmptySetOf
import java.security.PublicKey
import java.time.Instant
import java.util.*

// In a real system this would be a persistent map of hash to bytecode and we'd instantiate the object as needed inside
// a sandbox. For unit tests we just have a hard-coded list.
val TEST_PROGRAM_MAP: Map<Contract, Class<out Contract>> = mapOf(
        CASH_PROGRAM_ID to Cash::class.java,
        CP_PROGRAM_ID to CommercialPaper::class.java,
        JavaCommercialPaper.JCP_PROGRAM_ID to JavaCommercialPaper::class.java,
        DUMMY_PROGRAM_ID to DummyContract::class.java,
        IRS_PROGRAM_ID to InterestRateSwap::class.java
)

fun generateState() = DummyState(Random().nextInt())

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Defines a simple DSL for building pseudo-transactions (not the same as the wire protocol) for testing purposes.
//
// Define a transaction like this:
//
// transaction {
//    input { someExpression }
//    output { someExpression }
//    arg { someExpression }
//
//    tweak {
//         ... same thing but works with a copy of the parent, can add inputs/outputs/args just within this scope.
//    }
//
//    contract.accepts() -> should pass
//    contract `fails requirement` "some substring of the error message"
// }

// For Java compatibility please define helper methods here and then define the infix notation
object JavaTestHelpers {
    @JvmStatic fun ownedBy(state: Cash.State, owner: PublicKey) = state.copy(owner = owner)
    @JvmStatic fun issuedBy(state: Cash.State, party: Party) = state.copy(amount = Amount<Issued<Currency>>(state.amount.quantity, state.issuanceDef.copy(issuer = state.deposit.copy(party = party))))
    @JvmStatic fun issuedBy(state: Cash.State, deposit: PartyAndReference) = state.copy(amount = Amount<Issued<Currency>>(state.amount.quantity, state.issuanceDef.copy(issuer = deposit)))
    @JvmStatic fun withNotary(state: Cash.State, notary: Party) = TransactionState(state, notary)
    @JvmStatic fun withDeposit(state: Cash.State, deposit: PartyAndReference) = state.copy(amount = state.amount.copy(token = state.amount.token.copy(issuer = deposit)))

    @JvmStatic fun <T> at(state: Obligation.State<T>, dueBefore: Instant) = state.copy(template = state.template.copy(dueBefore = dueBefore))
    @JvmStatic fun <T> at(issuanceDef: Obligation.IssuanceDefinition<T>, dueBefore: Instant) = issuanceDef.copy(template = issuanceDef.template.copy(dueBefore = dueBefore))
    @JvmStatic fun <T> between(state: Obligation.State<T>, parties: Pair<Party, PublicKey>) = state.copy(obligor = parties.first, beneficiary = parties.second)
    @JvmStatic fun <T> ownedBy(state: Obligation.State<T>, owner: PublicKey) = state.copy(beneficiary = owner)
    @JvmStatic fun <T> issuedBy(state: Obligation.State<T>, party: Party) = state.copy(obligor = party)

    @JvmStatic fun ownedBy(state: CommercialPaper.State, owner: PublicKey) = state.copy(owner = owner)
    @JvmStatic fun withNotary(state: CommercialPaper.State, notary: Party) = TransactionState(state, notary)
    @JvmStatic fun ownedBy(state: ICommercialPaperState, new_owner: PublicKey) = state.withOwner(new_owner)

    @JvmStatic fun withNotary(state: ContractState, notary: Party) = TransactionState(state, notary)

    @JvmStatic fun CASH(amount: Amount<Currency>) = Cash.State(
            Amount<Issued<Currency>>(amount.quantity, Issued<Currency>(DUMMY_CASH_ISSUER, amount.token)),
            NullPublicKey)
    @JvmStatic fun STATE(amount: Amount<Issued<Currency>>) = Cash.State(amount, NullPublicKey)

    // Allows you to write 100.DOLLARS.OBLIGATION
    @JvmStatic fun OBLIGATION_DEF(issued: Issued<Currency>)
            = Obligation.StateTemplate(nonEmptySetOf(Cash().legalContractReference), nonEmptySetOf(issued), TEST_TX_TIME)
    @JvmStatic fun OBLIGATION(amount: Amount<Issued<Currency>>) = Obligation.State(Obligation.Lifecycle.NORMAL, MINI_CORP,
            OBLIGATION_DEF(amount.token), amount.quantity, NullPublicKey)
}


infix fun Cash.State.`owned by`(owner: PublicKey) = JavaTestHelpers.ownedBy(this, owner)
infix fun Cash.State.`issued by`(party: Party) = JavaTestHelpers.issuedBy(this, party)
infix fun Cash.State.`issued by`(deposit: PartyAndReference) = JavaTestHelpers.issuedBy(this, deposit)
infix fun Cash.State.`with notary`(notary: Party) = JavaTestHelpers.withNotary(this, notary)
infix fun Cash.State.`with deposit`(deposit: PartyAndReference): Cash.State = JavaTestHelpers.withDeposit(this, deposit)

infix fun <T> Obligation.State<T>.`at`(dueBefore: Instant) = JavaTestHelpers.at(this, dueBefore)
infix fun <T> Obligation.IssuanceDefinition<T>.`at`(dueBefore: Instant) = JavaTestHelpers.at(this, dueBefore)
infix fun <T> Obligation.State<T>.`between`(parties: Pair<Party, PublicKey>) = JavaTestHelpers.between(this, parties)
infix fun <T> Obligation.State<T>.`owned by`(owner: PublicKey) = JavaTestHelpers.ownedBy(this, owner)
infix fun <T> Obligation.State<T>.`issued by`(party: Party) = JavaTestHelpers.issuedBy(this, party)

infix fun CommercialPaper.State.`owned by`(owner: PublicKey) = JavaTestHelpers.ownedBy(this, owner)
infix fun CommercialPaper.State.`with notary`(notary: Party) = JavaTestHelpers.withNotary(this, notary)
infix fun ICommercialPaperState.`owned by`(new_owner: PublicKey) = JavaTestHelpers.ownedBy(this, new_owner)

infix fun ContractState.`with notary`(notary: Party) = JavaTestHelpers.withNotary(this, notary)

val DUMMY_CASH_ISSUER_KEY = generateKeyPair()
val DUMMY_CASH_ISSUER = Party("Snake Oil Issuer", DUMMY_CASH_ISSUER_KEY.public).ref(1)
/** Allows you to write 100.DOLLARS.CASH */
val Amount<Currency>.CASH: Cash.State get() = JavaTestHelpers.CASH(this)
val Amount<Issued<Currency>>.STATE: Cash.State get() = JavaTestHelpers.STATE(this)

/** Allows you to write 100.DOLLARS.CASH */
val Issued<Currency>.OBLIGATION_DEF: Obligation.StateTemplate<Currency> get() = JavaTestHelpers.OBLIGATION_DEF(this)
val Amount<Issued<Currency>>.OBLIGATION: Obligation.State<Currency> get() = JavaTestHelpers.OBLIGATION(this)
