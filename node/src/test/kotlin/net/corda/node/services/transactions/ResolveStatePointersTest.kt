package net.corda.node.services.transactions

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResolveStatePointersTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    private val notary = TestIdentity(DUMMY_NOTARY_NAME, 20)
    private val cordapps = listOf("net.corda.testing.contracts")
    private lateinit var services: MockServices

    @BelongsToContract(DummyContract::class)
    private data class Bar(
            override val participants: List<AbstractParty> = listOf(),
            val bar: Int = 0,
            val nestedPointer: LinearPointer<*>? = null,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : LinearState

    @BelongsToContract(DummyContract::class)
    private data class Foo<T : LinearState>(val baz: LinearPointer<T>, override val participants: List<AbstractParty>) : ContractState

    private val barOne = Bar(listOf(myself.party), 1)
    private val barTwo = Bar(listOf(myself.party), 2, LinearPointer(barOne.linearId, barOne::class.java))

    private fun createPointedToState(contractState: ContractState): StateAndRef<Bar> {
        // Create the pointed to state.
        return services.run {
            val tx = signInitialTransaction(TransactionBuilder(notary = notary.party, serviceHub = services).apply {
                addOutputState(contractState, DummyContract.PROGRAM_ID)
                addCommand(Command(DummyContract.Commands.Create(), myself.party.owningKey))
            })
            recordTransactions(listOf(tx))
            tx.tx.outRefsOfType<Bar>().single()
        }
    }

    @Before
    fun setUp() {
        val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(
                cordappPackages = cordapps,
                identityService = makeTestIdentityService(notary.identity, myself.identity),
                initialIdentity = myself,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = listOf(NotaryInfo(notary.party, true)))
        )
        services = databaseAndServices.second
    }

    @Test
    fun `resolve state pointers and check reference state is added to transaction`() {
        val stateAndRef = createPointedToState(barOne)
        val linearId = stateAndRef.state.data.linearId

        // Add a new state containing a linear pointer.
        val tx = TransactionBuilder(notary = notary.party, serviceHub = services).apply {
            val pointer = LinearPointer(linearId, barOne::class.java)
            addOutputState(Foo(pointer, listOf(myself.party)), DummyContract.PROGRAM_ID)
            addCommand(Command(DummyContract.Commands.Create(), myself.party.owningKey))
        }

        // Check the StateRef for the pointed-to state is added as a reference.
        assertEquals(stateAndRef.ref, tx.referenceStates().single())

        // Resolve the StateRef to the actual state.
        val ltx = tx.toLedgerTransaction(services)
        assertEquals(barOne, ltx.referenceStates.single())
    }

    @Test
    fun `resolving nested pointers is possible`() {
        // Create barOne.
        val barOneStateAndRef = createPointedToState(barOne)

        // Create another Bar - barTwo - which points to barOne.
        val barTwoStateAndRef = createPointedToState(barTwo)
        val barTwoLinearId = barTwoStateAndRef.state.data.linearId

        // Add a new state containing a linear pointer.
        val tx = TransactionBuilder(notary = notary.party, serviceHub = services).apply {
            val pointer = LinearPointer(barTwoLinearId, barTwo::class.java)
            addOutputState(Foo(pointer, listOf(myself.party)), DummyContract.PROGRAM_ID)
            addOutputState(Foo(pointer, listOf()), DummyContract.PROGRAM_ID)
            addCommand(Command(DummyContract.Commands.Create(), myself.party.owningKey))
        }

        tx.toLedgerTransaction(services).referenceStates.forEach { println(it) }

        // Check both Bar StateRefs have been added to the transaction.
        assertEquals(2, tx.referenceStates().size)
        assertEquals(setOf(barOneStateAndRef.ref, barTwoStateAndRef.ref), tx.referenceStates().toSet())
    }

    @Test
    fun `Resolving to an unknown state throws an exception`() {
        // Don't create the pointed to state.
        // Resolve the pointer for barTwo.
        assertFailsWith(IllegalStateException::class) {
            barTwo.nestedPointer?.resolve(services)
        }
    }

    @Test
    fun `resolving an exited state throws an exception`() {
        // Create barOne.
        val stateAndRef = createPointedToState(barOne)

        // Exit barOne from the ledger.
        services.run {
            val tx = signInitialTransaction(TransactionBuilder(notary = notary.party, serviceHub = services).apply {
                addInputState(stateAndRef)
                addCommand(Command(DummyContract.Commands.Move(), myself.party.owningKey))
            })
            recordTransactions(listOf(tx))
        }

        assertFailsWith(IllegalStateException::class) {
            barTwo.nestedPointer?.resolve(services)
        }
    }

    @Test
    fun `resolve linear pointer with correct type`() {
        val stateAndRef = createPointedToState(barOne)
        val linearPointer = LinearPointer(stateAndRef.state.data.linearId, barOne::class.java)
        val resolvedPointer = linearPointer.resolve(services)
        assertEquals(stateAndRef::class.java, resolvedPointer::class.java)
    }

    @Test
    fun `resolve state pointer in ledger transaction`() {
        val stateAndRef = createPointedToState(barOne)
        val linearId = stateAndRef.state.data.linearId

        // Add a new state containing a linear pointer.
        val tx = TransactionBuilder(notary = notary.party, serviceHub = services).apply {
            val pointer = LinearPointer(linearId, barOne::class.java)
            addOutputState(Foo(pointer, listOf(myself.party)), DummyContract.PROGRAM_ID)
            addCommand(Command(DummyContract.Commands.Create(), myself.party.owningKey))
        }

        val ltx = tx.toLedgerTransaction(services)
        @Suppress("UNCHECKED_CAST")
        val foo = ltx.outputs.single().data as Foo<Bar>
        assertEquals(stateAndRef, foo.baz.resolve(ltx))
    }
}