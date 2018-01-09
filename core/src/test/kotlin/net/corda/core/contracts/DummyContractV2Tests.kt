package net.corda.core.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.UpgradeCommand
import net.corda.core.node.ServicesForResolution
import net.corda.testing.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyContractV2
import net.corda.testing.internal.rigorousMock
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the version 2 dummy contract, to cover ensuring upgrade transactions are built correctly.
 */
class DummyContractV2Tests {
    private companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun `upgrade from v1`() {
        val services = rigorousMock<ServicesForResolution>().also {
            doReturn(rigorousMock<CordappProvider>()).whenever(it).cordappProvider
        }
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
