package net.corda.core.contracts

import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.ALICE_PUBKEY
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the version 2 dummy contract, to cover ensuring upgrade transactions are built correctly.
 */
class DummyContractV2Tests {
    @Test
    fun `upgrade from v1`() {
        val contract = DummyContractV2()
        val v1State = TransactionState(DummyContract.SingleOwnerState(0, ALICE_PUBKEY), DUMMY_NOTARY)
        val v1Ref = StateRef(SecureHash.randomSHA256(), 0)
        val v1StateAndRef = StateAndRef(v1State, v1Ref)
        val (tx, signers) = DummyContractV2().generateUpgradeFromV1(v1StateAndRef)

        assertEquals(v1Ref, tx.inputs.single())

        val expectedOutput: TransactionState<ContractState> = TransactionState(contract.upgrade(v1State.data), DUMMY_NOTARY)
        val actualOutput = tx.outputs.single()
        assertEquals(expectedOutput, actualOutput)

        val actualCommand = tx.commands.map { it.value }.single()
        assertTrue((actualCommand as UpgradeCommand<*>).oldContract is DummyContract)
        assertTrue(actualCommand.newContract is DummyContractV2)
    }
}
