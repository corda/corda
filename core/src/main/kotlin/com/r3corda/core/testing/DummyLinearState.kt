package com.r3corda.core.testing

import com.r3corda.core.contracts.Contract
import com.r3corda.core.contracts.LinearState
import com.r3corda.core.contracts.UniqueIdentifier
import com.r3corda.core.contracts.TransactionForContract
import com.r3corda.core.contracts.clauses.verifyClauses
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey

class DummyLinearContract: Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("Test")

    override fun verify(tx: TransactionForContract) {
        verifyClauses(tx,
                listOf(LinearState.ClauseVerifier(State::class.java)),
                emptyList())
    }


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
