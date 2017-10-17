package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.UpgradeCommand
import net.corda.testing.ALICE
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContractV2
import net.corda.testing.node.MockServices
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the version 2 dummy contract, to cover ensuring upgrade transactions are built correctly.
 */
class DummyContractV2Tests : TestDependencyInjectionBase() {
    @Test
    fun `upgrade from v1`() {
        val services = MockServices()
        val contractUpgrade = DummyContractV2()
        val v1State = TransactionState(DummyContract.SingleOwnerState(0, ALICE), DummyContract.PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint)
        val v1Ref = StateRef(SecureHash.randomSHA256(), 0)
        val v1StateAndRef = StateAndRef(v1State, v1Ref)
        val (tx, _) = DummyContractV2().generateUpgradeFromV1(services, v1StateAndRef)

        assertEquals(v1Ref, tx.inputs.single())

        val expectedOutput = TransactionState(contractUpgrade.upgrade(v1State.data), DummyContractV2.PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint)
        val actualOutput = tx.outputs.single()
        assertEquals(expectedOutput, actualOutput)

        val actualCommand = tx.commands.map { it.value }.single()
        assertTrue((actualCommand as UpgradeCommand).upgradedContractClass == DummyContractV2::class.java.name)
    }
}
