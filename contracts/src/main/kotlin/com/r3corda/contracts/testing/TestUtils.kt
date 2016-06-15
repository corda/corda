package com.r3corda.contracts.testing

import com.r3corda.contracts.*
import com.r3corda.contracts.cash.Cash
import com.r3corda.contracts.cash.CASH_PROGRAM_ID
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Contract
import com.r3corda.core.contracts.DummyContract
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.crypto.Party
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.MINI_CORP
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

fun generateState(notary: Party = DUMMY_NOTARY) = DummyContract.State(Random().nextInt(), notary)

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
//
// TODO: Make it impossible to forget to test either a failure or an accept for each transaction{} block

infix fun Cash.State.`owned by`(owner: PublicKey) = copy(owner = owner)
infix fun Cash.State.`issued by`(party: Party) = copy(deposit = deposit.copy(party = party))
infix fun CommercialPaper.State.`owned by`(owner: PublicKey) = this.copy(owner = owner)
infix fun ICommercialPaperState.`owned by`(new_owner: PublicKey) = this.withOwner(new_owner)

// Allows you to write 100.DOLLARS.CASH
val Amount<Currency>.CASH: Cash.State get() = Cash.State(MINI_CORP.ref(1, 2, 3), this, NullPublicKey, DUMMY_NOTARY)
