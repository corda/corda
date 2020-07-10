package net.corda.node.services.vault

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_SIZE
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.cordappsForPackages
import org.junit.BeforeClass
import org.junit.Test
import java.lang.IllegalArgumentException
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table
import kotlin.test.assertEquals

class VaultQueryJoinTest {
    companion object {
        private val mockNetwork = MockNetwork(
                MockNetworkParameters(
                        cordappsForAllNodes = cordappsForPackages(
                                listOf(
                                        "net.corda.node.services.vault"
                                )
                        )
                )
        )
        private val aliceNode = mockNetwork.createPartyNode(ALICE_NAME)
        private val notaryNode = mockNetwork.defaultNotaryNode
        private val serviceHubHandle = aliceNode.services
        private val createdStateRefs = mutableListOf<StateRef>()
        private const val numObjectsInLedger = DEFAULT_PAGE_SIZE + 1

        @BeforeClass
        @JvmStatic
        fun setup() {
            repeat(numObjectsInLedger) { index ->
                createdStateRefs.add(addSimpleObjectToLedger(DummyData(index)))
            }
        }

        private fun addSimpleObjectToLedger(dummyObject: DummyData): StateRef {
            val tx = TransactionBuilder(notaryNode.info.legalIdentities.first())
            tx.addOutputState(
                    DummyState(dummyObject, listOf(aliceNode.info.identityFromX500Name(ALICE_NAME)))
            )
            tx.addCommand(DummyContract.Commands.AddDummy(), aliceNode.info.legalIdentitiesAndCerts.first().owningKey)
            tx.verify(serviceHubHandle)
            val stx = serviceHubHandle.signInitialTransaction(tx)
            serviceHubHandle.recordTransactions(listOf(stx))
            return StateRef(stx.id, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `filter query with OR operator that returns only one tuple with pagination defined`() {
        val queryToCheckId = builder {
            val conditionToCheckId =
                    DummySchema.DummyState::id
                            .equal(0)
            QueryCriteria.VaultCustomQueryCriteria(conditionToCheckId, Vault.StateStatus.UNCONSUMED)
        }

        val queryToCheckStateRef =
                QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, stateRefs = listOf(createdStateRefs[numObjectsInLedger-1]))


        val results = serviceHubHandle.vaultService.queryBy<DummyState>(
                queryToCheckId.or(queryToCheckStateRef)
        )
        assertEquals(2, results.states.size)
        assertEquals(2, results.statesMetadata.size)
    }
}

object DummyStatesV

@Suppress("MagicNumber") // SQL column length
@CordaSerializable
object DummySchema : MappedSchema(schemaFamily = DummyStatesV.javaClass, version = 1, mappedTypes = listOf(DummyState::class.java)){

    @Entity
    @Table(name = "dummy_states", indexes = [Index(name = "dummy_id_index", columnList = "id")])
    class DummyState (
            @Column(name = "id", length = 4, nullable = false)
            var id: Int
    ) : PersistentState()
}

@CordaSerializable
data class DummyData(
        val id: Int
)

@BelongsToContract(DummyContract::class)
data class DummyState(val dummyData: DummyData, override val participants: List<AbstractParty>) :
        ContractState, QueryableState {
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DummySchema)


    override fun generateMappedObject(schema: MappedSchema) =
            when (schema) {
                is DummySchema -> DummySchema.DummyState(
                        dummyData.id
                )
                else -> throw IllegalArgumentException("Unsupported Schema")
            }
}

class DummyContract : Contract {
    override fun verify(tx: LedgerTransaction) { }
    interface Commands : CommandData {
        class AddDummy : Commands
    }
}