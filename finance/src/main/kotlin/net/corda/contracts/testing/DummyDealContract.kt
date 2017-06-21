package net.corda.contracts.testing

import net.corda.contracts.DealState
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TransactionForContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.containsAny
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

class DummyDealContract : Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("TestDeal")

    override fun verify(tx: TransactionForContract) {}

    data class State(
            override val contract: Contract = DummyDealContract(),
            override val participants: List<AbstractParty> = listOf(),
            override val linearId: UniqueIdentifier = UniqueIdentifier(),
            override val ref: String) : DealState {
        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { it.owningKey.containsAny(ourKeys) }
        }

        override fun generateAgreement(notary: Party): TransactionBuilder {
            throw UnsupportedOperationException("not implemented")
        }
    }
}
