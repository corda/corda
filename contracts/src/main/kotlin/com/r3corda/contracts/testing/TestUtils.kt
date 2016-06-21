package com.r3corda.contracts.testing

import com.r3corda.contracts.*
import com.r3corda.contracts.cash.CASH_PROGRAM_ID
import com.r3corda.contracts.cash.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Contract
import com.r3corda.core.contracts.DUMMY_PROGRAM_ID
import com.r3corda.core.contracts.DummyContract
import com.r3corda.core.contracts.PartyAndReference
import com.r3corda.core.contracts.Issued
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.TransactionState
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import java.security.PublicKey
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

fun generateState() = DummyContract.State(Random().nextInt())

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
object Java {
    @JvmStatic fun ownedBy(state: Cash.State, owner: PublicKey) = state.copy(owner = owner)
    @JvmStatic fun issuedBy(state: Cash.State, party: Party) = state.copy(amount = Amount<Issued<Currency>>(state.amount.quantity, state.issuanceDef.copy(issuer = state.deposit.copy(party = party))))
    @JvmStatic fun issuedBy(state: Cash.State, deposit: PartyAndReference) = state.copy(amount = Amount<Issued<Currency>>(state.amount.quantity, state.issuanceDef.copy(issuer = deposit)))
    @JvmStatic fun withNotary(state: Cash.State, notary: Party) = TransactionState(state, notary)
    @JvmStatic fun withDeposit(state: Cash.State, deposit: PartyAndReference) = state.copy(amount = state.amount.copy(token = state.amount.token.copy(issuer = deposit)))

    @JvmStatic fun ownedBy(state: CommercialPaper.State, owner: PublicKey) = state.copy(owner = owner)
    @JvmStatic fun withNotary(state: CommercialPaper.State, notary: Party) = TransactionState(state, notary)
    @JvmStatic fun ownedBy(state: ICommercialPaperState, new_owner: PublicKey) = state.withOwner(new_owner)

    @JvmStatic fun withNotary(state: ContractState, notary: Party) = TransactionState(state, notary)

    @JvmStatic fun CASH(amount: Amount<Currency>) = Cash.State(
            Amount<Issued<Currency>>(amount.quantity, Issued<Currency>(DUMMY_CASH_ISSUER, amount.token)),
            NullPublicKey)
    @JvmStatic fun STATE(amount: Amount<Issued<Currency>>) = Cash.State(amount, NullPublicKey)
}


infix fun Cash.State.`owned by`(owner: PublicKey) = Java.ownedBy(this, owner)
infix fun Cash.State.`issued by`(party: Party) = Java.issuedBy(this, party)
infix fun Cash.State.`issued by`(deposit: PartyAndReference) = Java.issuedBy(this, deposit)
infix fun Cash.State.`with notary`(notary: Party) = Java.withNotary(this, notary)
infix fun Cash.State.`with deposit`(deposit: PartyAndReference): Cash.State = Java.withDeposit(this, deposit)

infix fun CommercialPaper.State.`owned by`(owner: PublicKey) = Java.ownedBy(this, owner)
infix fun CommercialPaper.State.`with notary`(notary: Party) = Java.withNotary(this, notary)
infix fun ICommercialPaperState.`owned by`(new_owner: PublicKey) = Java.ownedBy(this, new_owner)

infix fun ContractState.`with notary`(notary: Party) = Java.withNotary(this, notary)

val DUMMY_CASH_ISSUER_KEY = generateKeyPair()
val DUMMY_CASH_ISSUER = Party("Snake Oil Issuer", DUMMY_CASH_ISSUER_KEY.public).ref(1)
/** Allows you to write 100.DOLLARS.CASH */
val Amount<Currency>.CASH: Cash.State get() = Java.CASH(this)
val Amount<Issued<Currency>>.STATE: Cash.State get() = Java.STATE(this)

