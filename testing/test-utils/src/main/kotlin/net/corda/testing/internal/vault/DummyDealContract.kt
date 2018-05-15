package net.corda.testing.internal.vault

import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.DealState

const val DUMMY_DEAL_PROGRAM_ID = "net.corda.testing.internal.vault.DummyDealContract"

class DummyDealContract : Contract {
    override fun verify(tx: LedgerTransaction) {}

    data class State(
            override val participants: List<AbstractParty>,
            override val linearId: UniqueIdentifier) : DealState, QueryableState {
        constructor(participants: List<AbstractParty> = listOf(),
                    ref: String) : this(participants, UniqueIdentifier(ref))

        override fun generateAgreement(notary: Party): TransactionBuilder {
            throw UnsupportedOperationException("not implemented")
        }

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DummyDealStateSchemaV1)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is DummyDealStateSchemaV1 -> DummyDealStateSchemaV1.PersistentDummyDealState(
                        participants = participants.toMutableSet(),
                        uid = linearId
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }
    }
}
