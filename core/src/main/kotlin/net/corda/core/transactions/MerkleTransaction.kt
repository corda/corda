package net.corda.core.transactions

import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.crypto.PartialMerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.createKryo
import net.corda.core.serialization.extendKryoHash
import net.corda.core.serialization.serialize
import java.util.*

/**
 * Build filtered transaction using provided filtering functions.
 */
fun WireTransaction.buildFilteredTransaction(filterFuns: FilterFuns): FilteredTransaction {
    return FilteredTransaction.buildMerkleTransaction(this, filterFuns)
}

/**
 * Calculation of all leaves hashes that are needed for calculation of transaction id and partial Merkle branches.
 */
fun WireTransaction.calculateLeavesHashes(): List<SecureHash> {
    val resultHashes = ArrayList<SecureHash>()
    val entries = listOf(inputs, outputs, attachments, commands)
    entries.forEach { it.mapTo(resultHashes, { x -> serializedHash(x) }) }
    return resultHashes
}

fun SecureHash.hashConcat(other: SecureHash) = (this.bytes + other.bytes).sha256()

fun <T : Any> serializedHash(x: T): SecureHash {
    val kryo = extendKryoHash(createKryo()) //Dealing with HashMaps inside states.
    return x.serialize(kryo).hash
}

/**
 * Creation and verification of a Merkle Tree for a Wire Transaction.
 *
 * See: https://en.wikipedia.org/wiki/Merkle_tree
 *
 * Transaction is split into following blocks: inputs, outputs, commands, attachments' refs. Merkle Tree is kept in
 * a recursive data structure. Building is done bottom up, from all leaves' hashes.
 * If a row in a tree has an odd number of elements - the final hash is hashed with itself.
 */
sealed class MerkleTree(val hash: SecureHash) {
    class Leaf(val value: SecureHash) : MerkleTree(value)
    class Node(val value: SecureHash, val left: MerkleTree, val right: MerkleTree) : MerkleTree(value)
    //DuplicatedLeaf is storing a hash of the rightmost node that had to be duplicated to obtain the tree.
    //That duplication can cause problems while building and verifying partial tree (especially for trees with duplicate
    //attachments or commands).
    class DuplicatedLeaf(val value: SecureHash) : MerkleTree(value)

    fun hashNodes(right: MerkleTree): MerkleTree {
        val newHash = this.hash.hashConcat(right.hash)
        return Node(newHash, this, right)
    }

    companion object {
        /**
         * Merkle tree building using hashes.
         */
        fun getMerkleTree(allLeavesHashes: List<SecureHash>): MerkleTree {
            val leaves = allLeavesHashes.map { MerkleTree.Leaf(it) }
            return buildMerkleTree(leaves)
        }

        /**
         * Tailrecursive function for building a tree bottom up.
         * @param lastNodesList MerkleTree nodes from previous level.
         * @return Tree root.
         */
        private tailrec fun buildMerkleTree(lastNodesList: List<MerkleTree>): MerkleTree {
            if (lastNodesList.size < 1)
                throw MerkleTreeException("Cannot calculate Merkle root on empty hash list.")
            if (lastNodesList.size == 1) {
                return lastNodesList[0] //Root reached.
            } else {
                val newLevelHashes: MutableList<MerkleTree> = ArrayList()
                var i = 0
                while (i < lastNodesList.size) {
                    val left = lastNodesList[i]
                    val n = lastNodesList.size
                    // If there is an odd number of elements at this level,
                    // the last element is hashed with itself and stored as a Leaf.
                    val right = when {
                        i + 1 > n - 1 -> MerkleTree.DuplicatedLeaf(lastNodesList[n - 1].hash)
                        else -> lastNodesList[i + 1]
                    }
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
 */
class FilteredLeaves(
        val inputs: List<StateRef>,
        val outputs: List<TransactionState<ContractState>>,
        val attachments: List<SecureHash>,
        val commands: List<Command>
) {
    fun getFilteredHashes(): List<SecureHash> {
        val resultHashes = ArrayList<SecureHash>()
        val entries = listOf(inputs, outputs, attachments, commands)
        entries.forEach { it.mapTo(resultHashes, { x -> serializedHash(x) }) }
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
            val filteredLeaves = FilteredLeaves(filteredInputs, filteredOutputs, filteredAttachments, filteredCommands)

            val pmt = PartialMerkleTree.build(wtx.merkleTree, filteredLeaves.getFilteredHashes())
            return FilteredTransaction(filteredLeaves, pmt)
        }
    }

    /**
     * Runs verification of Partial Merkle Branch with merkleRootHash.
     */
    fun verify(merkleRootHash: SecureHash): Boolean {
        val hashes: List<SecureHash> = filteredLeaves.getFilteredHashes()
        if (hashes.size == 0)
            throw MerkleTreeException("Transaction without included leaves.")
        return partialMerkleTree.verify(merkleRootHash, hashes)
    }
}
