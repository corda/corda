package net.corda.testing.internal.vault

import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

const val UNIQUE_DUMMY_LINEAR_CONTRACT_PROGRAM_ID = "net.corda.testing.internal.vault.UniqueDummyLinearContract"

class UniqueDummyLinearContract : Contract {
    override fun verify(tx: LedgerTransaction) {}

    data class State(
            override val participants: List<AbstractParty>,
            override val linearId: UniqueIdentifier) : LinearState, QueryableState {
        constructor(participants: List<AbstractParty> = listOf(),
                    ref: String) : this(participants, UniqueIdentifier(ref))

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(UniqueDummyLinearStateSchema)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return UniqueDummyLinearStateSchema.UniquePersistentLinearDummyState(id = linearId.externalId!!)
        }
    }
}

object UniqueDummyLinearStateSchema : MappedSchema(schemaFamily = UniqueDummyLinearStateSchema::class.java, version = 1, mappedTypes = listOf(UniquePersistentLinearDummyState::class.java)) {
    @Entity
    @Table(name = "unique_dummy_linear_state")
    class UniquePersistentLinearDummyState(
            @Column(unique = true)
            val id: String
    ) : PersistentState()
}