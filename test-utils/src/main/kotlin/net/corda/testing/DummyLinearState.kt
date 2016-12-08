package net.corda.testing

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.Clause
import net.corda.core.contracts.clauses.FilterOn
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import java.security.PublicKey

class DummyLinearContract: Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("Test")

    val clause: Clause<State, CommandData, Unit> = LinearState.ClauseVerifier()
    override fun verify(tx: TransactionForContract) = verifyClause(tx,
            FilterOn(clause, { states -> states.filterIsInstance<State>() }),
            emptyList())

    class State(
            override val linearId: UniqueIdentifier = UniqueIdentifier(),
            override val contract: Contract = DummyLinearContract(),
            override val participants: List<CompositeKey> = listOf(),
            val nonce: SecureHash = SecureHash.randomSHA256()) : LinearState {

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { it.containsAny(ourKeys) }
        }
    }
}
