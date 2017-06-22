package net.corda.contracts

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TransactionForContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.DummyDealStateSchemaV1
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

class DummyDealContract : Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("TestDeal")

    override fun verify(tx: TransactionForContract) {}

    data class State(
            override val contract: Contract = DummyDealContract(),
            override val participants: List<AbstractParty> = listOf(),
            override val linearId: UniqueIdentifier = UniqueIdentifier(),
            override val ref: String) : DealState, QueryableState
    {
        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { it.owningKey.containsAny(ourKeys) }
        }

        override fun generateAgreement(notary: Party): TransactionBuilder {
            throw UnsupportedOperationException("not implemented")
        }

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DummyDealStateSchemaV1)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is DummyDealStateSchemaV1 -> DummyDealStateSchemaV1.PersistentDummyDealState(
                        uid = linearId,
                        dealReference = ref
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }
    }
}
