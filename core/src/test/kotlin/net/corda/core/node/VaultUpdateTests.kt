package net.corda.core.node

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.DUMMY_NOTARY
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class VaultUpdateTests {
    private val DUMMY_PROGRAM_ID = "net.corda.core.node.VaultUpdateTests.DummyContract"
    private val noUpdate = Vault.Update(emptySet(), emptySet())

    object DummyContract : Contract {

        override fun verify(tx: LedgerTransaction) {
        }
    }

    private class DummyState : ContractState {
        override val participants: List<AbstractParty> = emptyList()
    }

    private val stateRef0 = StateRef(SecureHash.randomSHA256(), 0)
    private val stateRef1 = StateRef(SecureHash.randomSHA256(), 1)
    private val stateRef2 = StateRef(SecureHash.randomSHA256(), 2)
    private val stateRef3 = StateRef(SecureHash.randomSHA256(), 3)
    private val stateRef4 = StateRef(SecureHash.randomSHA256(), 4)

    private val stateAndRef0 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY), stateRef0)
    private val stateAndRef1 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY), stateRef1)
    private val stateAndRef2 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY), stateRef2)
    private val stateAndRef3 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY), stateRef3)
    private val stateAndRef4 = StateAndRef(TransactionState(DummyState(), DUMMY_PROGRAM_ID, DUMMY_NOTARY), stateRef4)

    @Test
    fun `nothing plus nothing is nothing`() {
        val before = noUpdate
        val after = before + noUpdate
        assertEquals(before, after)
    }

    @Test
    fun `something plus nothing is something`() {
        val before = Vault.Update<ContractState>(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef2, stateAndRef3))
        val after = before + noUpdate
        assertEquals(before, after)
    }

    @Test
    fun `nothing plus something is something`() {
        val before = noUpdate
        val after = before + Vault.Update<ContractState>(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef2, stateAndRef3))
        val expected = Vault.Update<ContractState>(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef2, stateAndRef3))
        assertEquals(expected, after)
    }

    @Test
    fun `something plus consume state 0 is something without state 0 output`() {
        val before = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1))
        val after = before + Vault.Update<ContractState>(setOf(stateAndRef0), setOf())
        val expected = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef1))
        assertEquals(expected, after)
    }

    @Test
    fun `something plus produce state 4 is something with additional state 4 output`() {
        val before = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1))
        val after = before + Vault.Update<ContractState>(setOf(), setOf(stateAndRef4))
        val expected = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1, stateAndRef4))
        assertEquals(expected, after)
    }

    @Test
    fun `something plus consume states 0 and 1, and produce state 4, is something without state 0 and 1 outputs and only state 4 output`() {
        val before = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1))
        val after = before + Vault.Update<ContractState>(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef4))
        val expected = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef4))
        assertEquals(expected, after)
    }

    @Test
    fun `can't combine updates of different types`() {
        val regularUpdate = Vault.Update<ContractState>(setOf(stateAndRef0, stateAndRef1), setOf(stateAndRef4))
        val notaryChangeUpdate = Vault.Update<ContractState>(setOf(stateAndRef2, stateAndRef3), setOf(stateAndRef0, stateAndRef1), type = Vault.UpdateType.NOTARY_CHANGE)
        assertFailsWith<IllegalArgumentException> { regularUpdate + notaryChangeUpdate }
    }
}
