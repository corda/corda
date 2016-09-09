package com.r3corda.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.contracts.clauses.Clause
import com.r3corda.core.contracts.clauses.verifyClause
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey

class DummyLinearContract: Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("Test")

    val clause: Clause<ContractState, CommandData, Unit> = LinearState.ClauseVerifier(State::class.java)
    override fun verify(tx: TransactionForContract) = verifyClause(tx,
            clause,
            emptyList())

    class State(
            override val linearId: UniqueIdentifier = UniqueIdentifier(),
            override val contract: Contract = DummyLinearContract(),
            override val participants: List<PublicKey> = listOf(),
            val nonce: SecureHash = SecureHash.randomSHA256()) : LinearState {

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { ourKeys.contains(it) }
        }
    }
}
