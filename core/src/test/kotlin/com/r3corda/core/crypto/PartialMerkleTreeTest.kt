package com.r3corda.core.crypto

import com.r3corda.contracts.asset.*
import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.contracts.`issued by`
import com.r3corda.core.contracts.`with notary`
import com.r3corda.core.serialization.serialize
import com.r3corda.core.transactions.FilterFuns
import com.r3corda.core.transactions.buildFilteredTransaction
import com.r3corda.core.transactions.getMerkleRoot
import com.r3corda.core.transactions.hashConcat
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.DUMMY_PUBKEY_1
import com.r3corda.testing.*


import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PartialMerkleTreeTest{
    val nodes = "abcdef"
    val hashed = nodes.map { it.serialize().sha256() }
    val root = SecureHash.Companion.parse("F6D8FB3720114F8D040D64F633B0D9178EB09A55AA7D62FAE1A070D1BF561051")

    private fun makeTX() = TransactionType.General.Builder(DUMMY_NOTARY).withItems(
            1000.DOLLARS.CASH `issued by` DUMMY_CASH_ISSUER `owned by` ALICE_PUBKEY `with notary` DUMMY_NOTARY)

    val testLedger = ledger {
        unverifiedTransaction {
            output("MEGA_CORP cash") {
                Cash.State(
                        amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                        owner = MEGA_CORP_PUBKEY
                )
            }
        }

        transaction {
            input("MEGA_CORP cash")
            output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
            command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
            this.verifies()
        }
    }

    val testTx = testLedger.interpreter.transactionsToVerify[0]

    //Building full Merkle Tree tests.
    @Test
    fun `building Merkle tree with 6 nodes - no rightmost nodes`(){
        assertEquals(6, hashed.size)
        val mr = getMerkleRoot(hashed)
        assertEquals(root, mr)
    }

    @Test
    fun `building Merkle tree one node`(){
        val node = 'a'.serialize().sha256()
        val mr = getMerkleRoot(listOf(node))
        assertEquals(node, mr)
    }

    @Test
    fun `building Merkle tree odd number of nodes`(){
        val odd = hashed.subList(0, 3)
        val h1 = hashed[0].hashConcat(hashed[1])
        val h2 = hashed[2].hashConcat(hashed[2])
        val expected = h1.hashConcat(h2)
        val root = getMerkleRoot(odd)
        assertEquals(root, expected)
    }

    @Test
    fun `building Merkle tree for a transaction`(){
        val filterFuns = FilterFuns(
                filterCommands = { x -> ALICE_PUBKEY in x.signers},
                filterOutputs = {true},
                filterInputs = {true})
        val mt = testTx.buildFilteredTransaction(filterFuns)
        assert(mt.verify(testTx.id))
    }

    @Test
    fun `ordering insensitive tree`(){
        val filterFuns = FilterFuns(filterCommands =  { x -> true })
        val tx1 = makeTX()
        tx1.addCommand(Cash.Commands.Issue(1), ALICE_PUBKEY)
        tx1.addCommand(Cash.Commands.Issue(0), ALICE_PUBKEY)
        val wtx1 = tx1.toWireTransaction()

        val tx2 = makeTX()
        tx2.addCommand(Cash.Commands.Issue(0), ALICE_PUBKEY)
        tx2.addCommand(Cash.Commands.Issue(1), ALICE_PUBKEY)
        val wtx2 = tx2.toWireTransaction()
        val mt1 = wtx1.buildFilteredTransaction(filterFuns)
        val mt2 = wtx2.buildFilteredTransaction(filterFuns)
        assertEquals(wtx1.id, wtx2.id)
        assert(mt1.verify(wtx1.id))
        assert(mt2.verify(wtx2.id))
    }

    //Partial Merkle Tree building tests
    @Test
    fun `Partial Merkle Tree, only left nodes branch`(){
        val includeLeaves = listOf(false, false, false, true, false, true)
        val inclHashes = listOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assert(!pmt.includeBranch[2] && !pmt.includeBranch[4]&& !pmt.includeBranch[8])
        assert(pmt.verify(inclHashes, root))

    }

    @Test
    fun `Partial Merkle Tree, include zero leaves`(){
        val includeLeaves = listOf(false, false, false, false, false, false)
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assertEquals(root, pmt.branchHashes[0])
        assert(pmt.verify(emptyList(), root))
    }

    @Test
    fun `Partial Merkle Tree, include all leaves`(){
        val includeLeaves = listOf(true, true, true, true, true, true)
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assert(pmt.verify(hashed, root))
    }

    @Test
    fun `verify Partial Merkle Tree - too many leaves failure`(){
        val includeLeaves = listOf(false, false, false, true, false, true)
        val inclHashes = listOf(hashed[3], hashed[5], hashed[0])
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assertFalse(pmt.verify(inclHashes, root))
    }

    @Test
    fun `verify Partial Merkle Tree - wrong root`(){
        val includeLeaves = listOf(false, false, false, true, false, true)
        val inclHashes = listOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        val wrongRoot = hashed[3].hashConcat(hashed[5])
        assertFalse(pmt.verify(inclHashes, wrongRoot))
    }
}
