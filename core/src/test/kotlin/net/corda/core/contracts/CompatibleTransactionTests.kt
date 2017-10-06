package net.corda.core.contracts

import net.corda.core.contracts.ComponentGroupEnum.*
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.ComponentVisibilityException
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.testing.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import org.junit.Test
import java.time.Instant
import java.util.function.Predicate
import kotlin.test.*

class CompatibleTransactionTests : TestDependencyInjectionBase() {

    private val dummyOutState = TransactionState(DummyState(0), DummyContract.PROGRAM_ID, DUMMY_NOTARY)
    private val stateRef1 = StateRef(SecureHash.randomSHA256(), 0)
    private val stateRef2 = StateRef(SecureHash.randomSHA256(), 1)
    private val stateRef3 = StateRef(SecureHash.randomSHA256(), 0)

    private val inputs = listOf(stateRef1, stateRef2, stateRef3) // 3 elements.
    private val outputs = listOf(dummyOutState, dummyOutState.copy(notary = BOB)) // 2 elements.
    private val commands = listOf(dummyCommand(DUMMY_KEY_1.public, DUMMY_KEY_2.public)) // 1 element.
    private val attachments = emptyList<SecureHash>() // Empty list.
    private val notary = DUMMY_NOTARY
    private val timeWindow = TimeWindow.fromOnly(Instant.now())
    private val privacySalt: PrivacySalt = PrivacySalt()

    private val inputGroup by lazy { ComponentGroup(INPUTS_GROUP.ordinal, inputs.map { it.serialize() }) }
    private val outputGroup by lazy { ComponentGroup(OUTPUTS_GROUP.ordinal, outputs.map { it.serialize() }) }
    private val commandGroup by lazy { ComponentGroup(COMMANDS_GROUP.ordinal, commands.map { it.serialize() }) }
    private val attachmentGroup by lazy { ComponentGroup(ATTACHMENTS_GROUP.ordinal, attachments.map { it.serialize() }) } // The list is empty.
    private val notaryGroup by lazy { ComponentGroup(NOTARY_GROUP.ordinal, listOf(notary.serialize())) }
    private val timeWindowGroup by lazy { ComponentGroup(TIMEWINDOW_GROUP.ordinal, listOf(timeWindow.serialize())) }

    private val newUnknownComponentGroup = ComponentGroup(20, listOf(OpaqueBytes(secureRandomBytes(4)), OpaqueBytes(secureRandomBytes(8))))
    private val newUnknownComponentEmptyGroup = ComponentGroup(21, emptyList())

    // Do not add attachments (empty list).
    private val componentGroupsA by lazy {
        listOf(
                inputGroup,
                outputGroup,
                commandGroup,
                notaryGroup,
                timeWindowGroup
        )
    }
    private val wireTransactionA by lazy { WireTransaction(componentGroups = componentGroupsA, privacySalt = privacySalt) }

    @Test
    fun `Merkle root computations`() {
        // Merkle tree computation is deterministic if the same salt and ordering are used.
        val wireTransactionB = WireTransaction(componentGroups = componentGroupsA, privacySalt = privacySalt)
        assertEquals(wireTransactionA, wireTransactionB)

        // Merkle tree computation will change if privacy salt changes.
        val wireTransactionOtherPrivacySalt = WireTransaction(componentGroups = componentGroupsA, privacySalt = PrivacySalt())
        assertNotEquals(wireTransactionA, wireTransactionOtherPrivacySalt)

        // Full Merkle root is computed from the list of Merkle roots of each component group.
        assertEquals(wireTransactionA.merkleTree.hash, MerkleTree.getMerkleTree(wireTransactionA.groupHashes).hash)

        // Trying to add an empty component group (not allowed), e.g. the empty attachmentGroup.
        val componentGroupsEmptyAttachment = listOf(
                inputGroup,
                outputGroup,
                commandGroup,
                attachmentGroup,
                notaryGroup,
                timeWindowGroup
        )
        assertFails { WireTransaction(componentGroups = componentGroupsEmptyAttachment, privacySalt = privacySalt) }

        // Ordering inside a component group matters.
        val inputsShuffled = listOf(stateRef2, stateRef1, stateRef3)
        val inputShuffledGroup = ComponentGroup(INPUTS_GROUP.ordinal, inputsShuffled.map { it -> it.serialize() })
        val componentGroupsB = listOf(
                inputShuffledGroup,
                outputGroup,
                commandGroup,
                notaryGroup,
                timeWindowGroup
        )
        val wireTransaction1ShuffledInputs = WireTransaction(componentGroups = componentGroupsB, privacySalt = privacySalt)
        // The ID has changed due to change of the internal ordering in inputs.
        assertNotEquals(wireTransaction1ShuffledInputs, wireTransactionA)

        // Inputs group Merkle roots are not equal.
        assertNotEquals(wireTransactionA.groupsMerkleRoots[INPUTS_GROUP.ordinal], wireTransaction1ShuffledInputs.groupsMerkleRoots[INPUTS_GROUP.ordinal])
        // But outputs group Merkle leaf (and the rest) remained the same.
        assertEquals(wireTransactionA.groupsMerkleRoots[OUTPUTS_GROUP.ordinal], wireTransaction1ShuffledInputs.groupsMerkleRoots[OUTPUTS_GROUP.ordinal])
        assertEquals(wireTransactionA.groupsMerkleRoots[NOTARY_GROUP.ordinal], wireTransaction1ShuffledInputs.groupsMerkleRoots[NOTARY_GROUP.ordinal])
        assertNull(wireTransactionA.groupsMerkleRoots[ATTACHMENTS_GROUP.ordinal])
        assertNull(wireTransaction1ShuffledInputs.groupsMerkleRoots[ATTACHMENTS_GROUP.ordinal])

        // Group leaves (components) ordering does not affect the id. In this case, we added outputs group before inputs.
        val shuffledComponentGroupsA = listOf(
                outputGroup,
                inputGroup,
                commandGroup,
                notaryGroup,
                timeWindowGroup
        )
        assertEquals(wireTransactionA, WireTransaction(componentGroups = shuffledComponentGroupsA, privacySalt = privacySalt))
    }

    @Test
    fun `WireTransaction constructors and compatibility`() {
        val wireTransactionOldConstructor = WireTransaction(inputs, attachments, outputs, commands, notary, timeWindow, privacySalt)
        assertEquals(wireTransactionA, wireTransactionOldConstructor)

        // Malformed tx - attachments is not List<SecureHash>. For this example, we mistakenly added input-state (StateRef) serialised objects with ATTACHMENTS_GROUP.ordinal.
        val componentGroupsB = listOf(
                inputGroup,
                outputGroup,
                commandGroup,
                ComponentGroup(ATTACHMENTS_GROUP.ordinal, inputGroup.components),
                notaryGroup,
                timeWindowGroup
        )
        assertFails { WireTransaction(componentGroupsB, privacySalt) }

        // Malformed tx - duplicated component group detected.
        val componentGroupsDuplicatedCommands = listOf(
                inputGroup,
                outputGroup,
                commandGroup, // First commandsGroup.
                commandGroup, // Second commandsGroup.
                notaryGroup,
                timeWindowGroup
        )
        assertFails { WireTransaction(componentGroupsDuplicatedCommands, privacySalt) }

        // Malformed tx - inputs is not a serialised object at all.
        val componentGroupsC = listOf(
                ComponentGroup(INPUTS_GROUP.ordinal, listOf(OpaqueBytes(ByteArray(8)))),
                outputGroup,
                commandGroup,
                notaryGroup,
                timeWindowGroup
        )
        assertFails { WireTransaction(componentGroupsC, privacySalt) }

        val componentGroupsCompatibleA = listOf(
                inputGroup,
                outputGroup,
                commandGroup,
                notaryGroup,
                timeWindowGroup,
                newUnknownComponentGroup // A new unknown component with ordinal 20 that we cannot process.
        )

        // The old client (receiving more component types than expected) is still compatible.
        val wireTransactionCompatibleA = WireTransaction(componentGroupsCompatibleA, privacySalt)
        assertEquals(wireTransactionCompatibleA.availableComponentGroups, wireTransactionA.availableComponentGroups) // The known components are the same.
        assertNotEquals(wireTransactionCompatibleA, wireTransactionA) // But obviously, its Merkle root has changed Vs wireTransactionA (which doesn't include this extra component).
        assertEquals(6, wireTransactionCompatibleA.componentGroups.size)

        // The old client will trhow if receiving an empty component (even if this unknown).
        val componentGroupsCompatibleEmptyNew = listOf(
                inputGroup,
                outputGroup,
                commandGroup,
                notaryGroup,
                timeWindowGroup,
                newUnknownComponentEmptyGroup // A new unknown component with ordinal 21 that we cannot process.
        )
        assertFails { WireTransaction(componentGroupsCompatibleEmptyNew, privacySalt) }
    }

    @Test
    fun `FilteredTransaction constructors and compatibility`() {
        // Filter out all of the components.
        val ftxNothing = wireTransactionA.buildFilteredTransaction(Predicate { false }) // Nothing filtered.
        assertEquals(6, ftxNothing.groupHashes.size) // Although nothing filtered, we still receive the group hashes for the top level Merkle tree.
        ftxNothing.verify()

        // Include all of the components.
        val ftxAll = wireTransactionA.buildFilteredTransaction(Predicate { true }) // All filtered.
        ftxAll.verify()
        ftxAll.checkAllComponentsVisible(INPUTS_GROUP)
        ftxAll.checkAllComponentsVisible(OUTPUTS_GROUP)
        ftxAll.checkAllComponentsVisible(COMMANDS_GROUP)
        ftxAll.checkAllComponentsVisible(ATTACHMENTS_GROUP)
        ftxAll.checkAllComponentsVisible(NOTARY_GROUP)
        ftxAll.checkAllComponentsVisible(TIMEWINDOW_GROUP)

        // Filter inputs only.
        fun filtering(elem: Any): Boolean {
            return when (elem) {
                is StateRef -> true
                else -> false
            }
        }

        val ftxInputs = wireTransactionA.buildFilteredTransaction(Predicate(::filtering)) // Inputs only filtered.
        ftxInputs.verify()
        ftxInputs.checkAllComponentsVisible(INPUTS_GROUP)

        assertEquals(1, ftxInputs.filteredComponentGroups.size) // We only add component groups that are not empty, thus in this case: the inputs only.
        assertEquals(3, ftxInputs.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.components.size) // All 3 inputs are present.
        assertEquals(3, ftxInputs.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.nonces.size) // And their corresponding nonces.
        assertNotNull(ftxInputs.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.partialMerkleTree) // And the Merkle tree.

        // Filter one input only.
        fun filteringOneInput(elem: Any) = elem == inputs[0]

        val ftxOneInput = wireTransactionA.buildFilteredTransaction(Predicate(::filteringOneInput)) // First input only filtered.
        ftxOneInput.verify()
        assertFailsWith<ComponentVisibilityException> { ftxOneInput.checkAllComponentsVisible(INPUTS_GROUP) }

        assertEquals(1, ftxOneInput.filteredComponentGroups.size) // We only add component groups that are not empty, thus in this case: the inputs only.
        assertEquals(1, ftxOneInput.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.components.size) // 1 input is present.
        assertEquals(1, ftxOneInput.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.nonces.size) // And its corresponding nonce.
        assertNotNull(ftxOneInput.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.partialMerkleTree) // And the Merkle tree.

        // The old client (receiving more component types than expected) is still compatible.
        val componentGroupsCompatibleA = listOf(inputGroup,
                outputGroup,
                commandGroup,
                notaryGroup,
                timeWindowGroup,
                newUnknownComponentGroup // A new unknown component with ordinal 10,000 that we cannot process.
        )
        val wireTransactionCompatibleA = WireTransaction(componentGroupsCompatibleA, privacySalt)
        val ftxCompatible = wireTransactionCompatibleA.buildFilteredTransaction(Predicate(::filtering))
        ftxCompatible.verify()
        assertEquals(ftxInputs.inputs, ftxCompatible.inputs)
        assertEquals(wireTransactionCompatibleA.id, ftxCompatible.id)

        assertEquals(1, ftxCompatible.filteredComponentGroups.size)
        assertEquals(3, ftxCompatible.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.components.size)
        assertEquals(3, ftxCompatible.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.nonces.size)
        assertNotNull(ftxCompatible.filteredComponentGroups.firstOrNull { it.groupIndex == INPUTS_GROUP.ordinal }!!.partialMerkleTree)

        // Now, let's allow everything, including the new component type that we cannot process.
        val ftxCompatibleAll = wireTransactionCompatibleA.buildFilteredTransaction(Predicate { true }) // All filtered, including the unknown component.
        ftxCompatibleAll.verify()
        assertEquals(wireTransactionCompatibleA.id, ftxCompatibleAll.id)

        // Check we received the last (6th) element that we cannot process (backwards compatibility).
        assertEquals(6, ftxCompatibleAll.filteredComponentGroups.size)


    }
}
