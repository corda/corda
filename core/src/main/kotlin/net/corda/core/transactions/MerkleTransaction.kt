package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.crypto.PartialMerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.createKryo
import net.corda.core.serialization.extendKryoHash
import net.corda.core.serialization.serialize
import java.util.*

fun SecureHash.hashConcat(other: SecureHash) = (this.bytes + other.bytes).sha256()

fun <T : Any> serializedHash(x: T): SecureHash {
    val kryo = extendKryoHash(createKryo()) // Dealing with HashMaps inside states.
    return x.serialize(kryo).hash
}

val zeroHash = SecureHash.SHA256(ByteArray(32, { 0.toByte() }))

/**
 * Creation and verification of a Merkle Tree for a Wire Transaction.
 *
 * See: https://en.wikipedia.org/wiki/Merkle_tree
 *
 * Transaction is split into following blocks: inputs, outputs, commands, attachments' refs, timestamp, notary,
 * signers (as whole - sorted), tx type. Merkle Tree is kept in a recursive data structure. Building is done bottom up,
 * from all leaves' hashes. If number of leaves is not a power of two, the tree is padded with zero hashes.
 */
sealed class MerkleTree(val hash: SecureHash) {
    class Leaf(val value: SecureHash) : MerkleTree(value)
    class Node(val value: SecureHash, val left: MerkleTree, val right: MerkleTree) : MerkleTree(value)

    private fun hashNodes(right: MerkleTree): MerkleTree {
        val newHash = this.hash.hashConcat(right.hash)
        return Node(newHash, this, right)
    }

    // Check if a tree is full binary tree. Returns the height of the tree if full, otherwise throws exception.
    @Throws(MerkleTreeException::class)
    fun checkFull(level: Int = 0): Int {
        return when (this) {
            is Leaf -> level
            is Node -> {
                val l1 = this.left.checkFull(level+1)
                val l2 = this.right.checkFull(level+1)
                if (l1 != l2) throw MerkleTreeException("Got not full binary tree.")
                l1
            }
        }
    }

    companion object {
        private fun isPow2(num: Int): Boolean = num and (num-1) == 0

        /**
         * Merkle tree building using hashes, with zero hash padding to full power of 2.
         */
        @Throws(IllegalArgumentException::class)
        fun getMerkleTree(allLeavesHashes: List<SecureHash>): MerkleTree {
            val leaves = padWithZeros(allLeavesHashes).map { MerkleTree.Leaf(it) }
            return buildMerkleTree(leaves)
        }

        // If number of leaves in the tree is not a power of 2, we need to pad it with zero hashes.
        fun padWithZeros(allLeavesHashes: List<SecureHash>): List<SecureHash> {
            var n = allLeavesHashes.size
            if (isPow2(n)) return allLeavesHashes
            val paddedHashes = ArrayList<SecureHash>(allLeavesHashes)
            while (!isPow2(n)) {
                paddedHashes.add(zeroHash)
                n++
            }
            return paddedHashes
        }

        /**
         * Tailrecursive function for building a tree bottom up.
         * @param lastNodesList MerkleTree nodes from previous level.
         * @return Tree root.
         */
        private tailrec fun buildMerkleTree(lastNodesList: List<MerkleTree>): MerkleTree {
            if (lastNodesList.isEmpty())
                throw MerkleTreeException("Cannot calculate Merkle root on empty hash list.")
            if (lastNodesList.size == 1) {
                return lastNodesList[0] //Root reached.
            } else {
                val newLevelHashes: MutableList<MerkleTree> = ArrayList()
                var i = 0
                val n = lastNodesList.size
                while (i < n) {
                    val left = lastNodesList[i]
                    require(i+1 <= n-1) { "Sanity check: number of nodes should be even." }
                    val right = lastNodesList[i+1]
                    val combined = left.hashNodes(right)
                    newLevelHashes.add(combined)
                    i += 2
                }
                return buildMerkleTree(newLevelHashes)
            }
        }
    }
}

/**
 * Class that holds filtered leaves for a partial Merkle transaction. We assume mixed leaves types.
 * Notice that we include only certain parts of wire transaction (no type, signers, notary). Timestamp is always added,
 * if present.
 */
class FilteredLeaves(
        val inputs: List<StateRef>,
        val outputs: List<TransactionState<ContractState>>,
        val attachments: List<SecureHash>,
        val commands: List<Command>,
        val timestamp: Timestamp?
) {
    fun getFilteredHashes(): List<SecureHash> {
        val resultHashes = ArrayList<SecureHash>()
        val entries = listOf(inputs, outputs, attachments, commands)
        entries.forEach { it.mapTo(resultHashes, { x -> serializedHash(x) }) }
        if (timestamp != null)
            resultHashes.add(serializedHash(timestamp))
        return resultHashes
    }
}

/**
 * Holds filter functions on transactions fields.
 * Functions are used to build a partial tree only out of some subset of original transaction fields.
 */
class FilterFuns(
        val filterInputs: (StateRef) -> Boolean = { false },
        val filterOutputs: (TransactionState<ContractState>) -> Boolean = { false },
        val filterAttachments: (SecureHash) -> Boolean = { false },
        val filterCommands: (Command) -> Boolean = { false }
) {
    fun <T : Any> genericFilter(elem: T): Boolean {
        return when (elem) {
            is StateRef -> filterInputs(elem)
            is TransactionState<*> -> filterOutputs(elem)
            is SecureHash -> filterAttachments(elem)
            is Command -> filterCommands(elem)
            else -> throw IllegalArgumentException("Wrong argument type: ${elem.javaClass}")
        }
    }
}

/**
 * Class representing merkleized filtered transaction.
 * @param filteredLeaves Leaves included in a filtered transaction.
 * @param partialMerkleTree Merkle branch needed to verify filteredLeaves.
 */
class FilteredTransaction(
        val filteredLeaves: FilteredLeaves,
        val partialMerkleTree: PartialMerkleTree
) {
    companion object {
        /**
         * Construction of filtered transaction with Partial Merkle Tree.
         * @param wtx WireTransaction to be filtered.
         * @param filterFuns filtering functions for inputs, outputs, attachments, commands.
         */
        fun buildMerkleTransaction(wtx: WireTransaction,
                                   filterFuns: FilterFuns
        ): FilteredTransaction {
            val filteredInputs = wtx.inputs.filter { filterFuns.genericFilter(it) }
            val filteredOutputs = wtx.outputs.filter { filterFuns.genericFilter(it) }
            val filteredAttachments = wtx.attachments.filter { filterFuns.genericFilter(it) }
            val filteredCommands = wtx.commands.filter { filterFuns.genericFilter(it) }
            val filteredLeaves = FilteredLeaves(filteredInputs, filteredOutputs, filteredAttachments, filteredCommands, wtx.timestamp)
            val pmt = PartialMerkleTree.build(wtx.merkleTree, filteredLeaves.getFilteredHashes())
            return FilteredTransaction(filteredLeaves, pmt)
        }
    }

    /**
     * Runs verification of Partial Merkle Branch with merkleRootHash.
     */
    fun verify(merkleRootHash: SecureHash): Boolean {
        val hashes: List<SecureHash> = filteredLeaves.getFilteredHashes()
        if (hashes.isEmpty())
            throw MerkleTreeException("Transaction without included leaves.")
        return partialMerkleTree.verify(merkleRootHash, hashes)
    }
}
