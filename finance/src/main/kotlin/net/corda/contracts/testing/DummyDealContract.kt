package net.corda.contracts.testing

import net.corda.core.contracts.Contract
import net.corda.core.contracts.DealState
import net.corda.core.contracts.TransactionForContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.*
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

class DummyDealContract : Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("TestDeal")

    override fun verify(tx: TransactionForContract) {}

    data class State(
            override val contract: Contract = DummyDealContract(),
            override val participants: List<PublicKey> = listOf(),
            override val linearId: UniqueIdentifier = UniqueIdentifier(),
            override val ref: String,
            override val parties: List<AnonymousParty> = listOf()) : DealState {
        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { it.containsAny(ourKeys) }
        }

        override fun generateAgreement(notary: Party): TransactionBuilder {
            throw UnsupportedOperationException("not implemented")
        }
    }
}
