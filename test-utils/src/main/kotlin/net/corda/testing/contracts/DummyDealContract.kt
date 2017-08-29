package net.corda.testing.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.DealState
import net.corda.testing.schemas.DummyDealStateSchemaV1

class DummyDealContract : Contract {
    override fun verify(tx: LedgerTransaction) {}

    data class State(
            override val contract: Contract,
            override val participants: List<AbstractParty>,
            override val linearId: UniqueIdentifier) : DealState, QueryableState
    {
        constructor(contract: Contract = DummyDealContract(),
                    participants: List<AbstractParty> = listOf(),
                    ref: String) : this(contract, participants, UniqueIdentifier(ref))

        override val executableAttachmentsValidator get() = AlwaysAcceptExecutableAttachmentsValidator

        override fun generateAgreement(notary: Party): TransactionBuilder {
            throw UnsupportedOperationException("not implemented")
        }

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DummyDealStateSchemaV1)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is DummyDealStateSchemaV1 -> DummyDealStateSchemaV1.PersistentDummyDealState(
                        _participants = participants.toSet(),
                        uid = linearId
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }
    }
}
