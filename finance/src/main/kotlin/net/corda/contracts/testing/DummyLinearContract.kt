package net.corda.contracts.testing

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.Clause
import net.corda.core.contracts.clauses.FilterOn
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.containsAny
import net.corda.core.identity.AbstractParty
import java.security.PublicKey

class DummyLinearContract : Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("Test")

    val clause: Clause<State, CommandData, Unit> = LinearState.ClauseVerifier()
    override fun verify(tx: TransactionForContract) = verifyClause(tx,
            FilterOn(clause, { states -> states.filterIsInstance<State>() }),
            emptyList())

    data class State(
            override val linearId: UniqueIdentifier = UniqueIdentifier(),
            override val contract: Contract = DummyLinearContract(),
            override val participants: List<AbstractParty> = listOf(),
            val nonce: SecureHash = SecureHash.randomSHA256()) : LinearState {

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { it.owningKey.containsAny(ourKeys) }
        }
    }
}