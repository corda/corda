package net.corda.testing.internal.vault

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.core.DummyCommandData
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

const val UNIQUE_DUMMY_FUNGIBLE_CONTRACT_PROGRAM_ID = "net.corda.testing.internal.vault.UniqueDummyFungibleContract"

class UniqueDummyFungibleContract : Contract {
    override fun verify(tx: LedgerTransaction) {}

    data class State(override val amount: Amount<Issued<Currency>>,
                     override val owner: AbstractParty) : FungibleAsset<Currency>, QueryableState {

        override val exitKeys = setOf(owner.owningKey, amount.token.issuer.party.owningKey)
        override val participants = listOf(owner)

        override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Currency>>, newOwner: AbstractParty): FungibleAsset<Currency> = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(DummyCommandData, copy(owner = newOwner))

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(UniqueDummyFungibleStateSchema)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return UniqueDummyFungibleStateSchema.UniquePersistentDummyFungibleState(currency = amount.token.product.currencyCode)
        }
    }
}

object UniqueDummyFungibleStateSchema : MappedSchema(schemaFamily = UniqueDummyFungibleStateSchema::class.java, version = 1, mappedTypes = listOf(UniquePersistentDummyFungibleState::class.java)) {
    @Entity
    @Table(name = "unique_dummy_fungible_state")
    class UniquePersistentDummyFungibleState(
            @Column(unique = true)
            val currency: String
    ) : PersistentState()
}