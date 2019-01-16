package net.corda.core.internal

import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.utilities.OpaqueBytes
import org.junit.Test
import kotlin.test.assertEquals

class StatePointerSearchTests {

    private val partyAndRef = PartyAndReference(AnonymousParty(NullKeys.NullPublicKey), OpaqueBytes.of(0))

    private data class StateWithGeneric(val amount: Amount<Issued<LinearPointer<LinearState>>>) : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }

    private data class StateWithList(val pointerList: List<LinearPointer<LinearState>>) : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }

    private data class StateWithMap(val pointerMap: Map<Any, Any>) : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }

    private data class StateWithSet(val pointerSet: Set<LinearPointer<LinearState>>) : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }

    private data class StateWithListOfList(val pointerSet: List<List<LinearPointer<LinearState>>>) : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }

    @Test
    fun `find pointer in state with generic type`() {
        val linearPointer = LinearPointer(UniqueIdentifier(), LinearState::class.java)
        val testState = StateWithGeneric(Amount(100L, Issued(partyAndRef, linearPointer)))
        val results = StatePointerSearch(testState).search()
        assertEquals(results, setOf(linearPointer))
    }

    @Test
    fun `find pointers which are inside a list`() {
        val linearPointerOne = LinearPointer(UniqueIdentifier(), LinearState::class.java)
        val linearPointerTwo = LinearPointer(UniqueIdentifier(), LinearState::class.java)
        val testState = StateWithList(listOf(linearPointerOne, linearPointerTwo))
        val results = StatePointerSearch(testState).search()
        assertEquals(results, setOf(linearPointerOne, linearPointerTwo))
    }

    @Test
    fun `find pointers which are inside a map`() {
        val linearPointerOne = LinearPointer(UniqueIdentifier(), LinearState::class.java)
        val linearPointerTwo = LinearPointer(UniqueIdentifier(), LinearState::class.java)
        val testState = StateWithMap(mapOf(linearPointerOne to 1, 2 to linearPointerTwo))
        val results = StatePointerSearch(testState).search()
        assertEquals(results, setOf(linearPointerOne, linearPointerTwo))
    }

    @Test
    fun `find pointers which are inside a set`() {
        val linearPointer = LinearPointer(UniqueIdentifier(), LinearState::class.java)
        val testState = StateWithSet(setOf(linearPointer))
        val results = StatePointerSearch(testState).search()
        assertEquals(results, setOf(linearPointer))
    }

    @Test
    fun `find pointers which are inside nested iterables`() {
        val linearPointer = LinearPointer(UniqueIdentifier(), LinearState::class.java)
        val testState = StateWithListOfList(listOf(listOf(linearPointer)))
        val results = StatePointerSearch(testState).search()
        assertEquals(results, setOf(linearPointer))
    }

}