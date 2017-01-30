package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.crypto.SecureHash.Companion.zeroHash
import net.corda.core.serialization.createKryo
import net.corda.core.serialization.extendKryoHash
import net.corda.core.serialization.serialize
import java.util.*

fun <T : Any> serializedHash(x: T): SecureHash {
    val kryo = extendKryoHash(createKryo()) // Dealing with HashMaps inside states.
    return x.serialize(kryo).hash
}

/**
 * Creation and verification of a Merkle Tree for a Wire Transaction.
 *
 * See: https://en.wikipedia.org/wiki/Merkle_tree
 *
 * Transaction is split into following blocks: inputs, attachments' refs, outputs, commands, notary,
 * signers, tx type, timestamp. Merkle Tree is kept in a recursive data structure. Building is done bottom up,
 * from all leaves' hashes. If number of leaves is not a power of two, the tree is padded with zero hashes.
 */
sealed class MerkleTree(val hash: SecureHash) {
    class Leaf(val value: SecureHash) : MerkleTree(value)
    class Node(val value: SecureHash, val left: MerkleTree, val right: MerkleTree) : MerkleTree(value)

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
        private fun padWithZeros(allLeavesHashes: List<SecureHash>): List<SecureHash> {
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
                    val newHash = left.hash.hashConcat(right.hash)
                    val combined = Node(newHash, left, right)
                    newLevelHashes.add(combined)
                    i += 2
                }
                return buildMerkleTree(newLevelHashes)
            }
        }
    }
}

/**
 * Interface implemented by WireTransaction and FilteredLeaves.
 * Property traversableList assures that we always calculate hashes in the same order, lets us define which
 * fields of WireTransaction will be included in id calculation or partial merkle tree building.
 */
interface TraversableTransaction {
    val inputs: List<StateRef>
    val attachments: List<SecureHash>
    val outputs: List<TransactionState<ContractState>>
    val commands: List<Command>
    val notary: Party?
    val mustSign: List<CompositeKey>
    val type: TransactionType?
    val timestamp: Timestamp?

    /**
     * Traversing transaction fields with a list function over transaction contents. Used for leaves hashes calculation
     * and user provided filtering and checking of filtered transaction.
     */
    // We may want to specify our own behaviour on certain tx fields.
    // Like if we include them at all, what to do with null values, if we treat list as one or not etc. for building
    // torn-off transaction and id calculation.
    val traversableList: List<Any>
        get() {
            val traverseList = mutableListOf(inputs, attachments, outputs, commands).flatten().toMutableList()
            if (notary != null) traverseList.add(notary!!)
            traverseList.addAll(mustSign)
            if (type != null) traverseList.add(type!!)
            if (timestamp != null) traverseList.add(timestamp!!)
            return traverseList
        }

    // Calculation of all leaves hashes that are needed for calculation of transaction id and partial Merkle branches.
    fun calculateLeavesHashes(): List<SecureHash> = traversableList.map { serializedHash(it) }
}

/**
 * Class that holds filtered leaves for a partial Merkle transaction. We assume mixed leaf types, notice that every
 * field from WireTransaction can be used in PartialMerkleTree calculation.
 */
class FilteredLeaves(
        override val inputs: List<StateRef>,
        override val attachments: List<SecureHash>,
        override val outputs: List<TransactionState<ContractState>>,
        override val commands: List<Command>,
        override val notary: Party?,
        override val mustSign: List<CompositeKey>,
        override val type: TransactionType?,
        override val timestamp: Timestamp?
) : TraversableTransaction {
    /**
     * Function that checks the whole filtered structure.
     * Force type checking on a structure that we obtained, so we don't sign more than expected.
     * Example: Oracle is implemented to check only for commands, if it gets an attachment and doesn't expect it - it can sign
     * over a transaction with the attachment that wasn't verified. Of course it depends on how you implement it, but else -> false
     * should solve a problem with possible later extensions to WireTransaction.
     * @param checkingFun function that performs type checking on the structure fields and provides verification logic accordingly.
     * @returns false if no elements were matched on a structure or checkingFun returned false.
     */
    fun checkWithFun(checkingFun: (Any) -> Boolean): Boolean {
        val checkList = traversableList.map { checkingFun(it) }
        return (!checkList.isEmpty()) && checkList.all { true }
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
         * @param filtering filtering over the whole WireTransaction
         */
        fun buildMerkleTransaction(wtx: WireTransaction,
                                   filtering: (Any) -> Boolean
        ): FilteredTransaction {
            val filteredLeaves = wtx.filterWithFun(filtering)
            val merkleTree = wtx.getMerkleTree()
            val pmt = PartialMerkleTree.build(merkleTree, filteredLeaves.calculateLeavesHashes())
            return FilteredTransaction(filteredLeaves, pmt)
        }
    }

    /**
     * Runs verification of Partial Merkle Branch with merkleRootHash.
     */
    @Throws(MerkleTreeException::class)
    fun verify(merkleRootHash: SecureHash): Boolean {
        val hashes: List<SecureHash> = filteredLeaves.calculateLeavesHashes()
        if (hashes.isEmpty())
            throw MerkleTreeException("Transaction without included leaves.")
        return partialMerkleTree.verify(merkleRootHash, hashes)
    }

    /**
     * Runs verification of Partial Merkle Branch with merkleRootHash. Checks filteredLeaves with provided checkingFun.
     */
    @Throws(MerkleTreeException::class)
    fun verifyWithFunction(merkleRootHash: SecureHash, checkingFun: (Any) -> Boolean): Boolean {
        return verify(merkleRootHash) && filteredLeaves.checkWithFun { checkingFun(it) }
    }
}
