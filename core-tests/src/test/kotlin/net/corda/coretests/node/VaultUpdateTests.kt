package net.corda.coretests.node

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VaultUpdateTests {
    private companion object {
        const val DUMMY_PROGRAM_ID = "net.corda.coretests.node.VaultUpdateTests\$DummyContract"
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val emptyUpdate = Vault.Update(emptySet<StateAndRef<*>>(), emptySet(), type = Vault.UpdateType.GENERAL, references = emptySet())
    }

    object DummyContract : Contract {

        override fun verify(tx: LedgerTransaction) {
        }
    }

    @BelongsToContract(DummyContract::class)
    private class DummyState : ContractState {
        override val participants: List<AbstractParty> = emptyList()
    }

    private val stateRef0 = StateRef(SecureHash.randomSHA256(), 0)
    private val stateRef1 = StateRef(SecureHash.randomSHA256(), 1)
    private val stateRef2 = StateRef(SecureHash.randomSHA256(), 2)
    private val stateRef3 = StateRef(SecureHash.randomSHA256(), 3)
    private val stateRef4 = StateRef(SecureHash.randomSHA256(), 4)

    private val stateAndRef0 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint), stateRef0)
    private val stateAndRef1 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint), stateRef1)
    private val stateAndRef2 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint), stateRef2)
    private val stateAndRef3 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint), stateRef3)
    private val stateAndRef4 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint), stateRef4)

    @Test(timeout=300_000)
	fun `nothing plus nothing is nothing`() {
        val before = emptyUpdate
        val after = before + emptyUpdate
        assertEquals(before, after)
    }

    @Test(timeout=300_000)
	fun `something plus nothing is something`() {
        val before = Vault.Update<ContractState>(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef2, stateAndRef3))
        val after = before + emptyUpdate
        assertEquals(before, after)
    }

    @Test(timeout=300_000)
	fun `nothing plus something is something`() {
        val before = emptyUpdate
        val after = before + Vault.Update(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef2, stateAndRef3))
        val expected = Vault.Update<ContractState>(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef2, stateAndRef3))
        assertEquals(expected, after)
    }

    @Test(timeout=300_000)
	fun `something plus consume state 0 is something without state 0 output`() {
        val before = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1))
        val after = before + Vault.Update(setOf(stateAndRef0), setOf())
        val expected = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef1))
        assertEquals(expected, after)
    }

    @Test(timeout=300_000)
	fun `something plus produce state 4 is something with additional state 4 output`() {
        val before = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1))
        val after = before + Vault.Update(setOf(), setOf(stateAndRef4))
        val expected = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1, stateAndRef4))
        assertEquals(expected, after)
    }

    @Test(timeout=300_000)
	fun `something plus consume states 0 and 1, and produce state 4, is something without state 0 and 1 outputs and only state 4 output`() {
        val before = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1))
        val after = before + Vault.Update(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef4))
        val expected = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef4))
        assertEquals(expected, after)
    }

    @Test(timeout=300_000)
	fun `can't combine updates of different types`() {
        val regularUpdate = Vault.Update<ContractState>(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef4))
        val notaryChangeUpdate = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1), type = Vault.UpdateType.NOTARY_CHANGE)
        assertFailsWith<IllegalArgumentException> { regularUpdate + notaryChangeUpdate }
    }
}
