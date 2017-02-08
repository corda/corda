package net.corda.core.crypto

import java.util.*

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
            val leaves = padWithZeros(allLeavesHashes).map { Leaf(it) }
            return buildMerkleTree(leaves)
        }

        // If number of leaves in the tree is not a power of 2, we need to pad it with zero hashes.
        private fun padWithZeros(allLeavesHashes: List<SecureHash>): List<SecureHash> {
            var n = allLeavesHashes.size
            if (isPow2(n)) return allLeavesHashes
            val paddedHashes = ArrayList<SecureHash>(allLeavesHashes)
            while (!isPow2(n)) {
                paddedHashes.add(SecureHash.zeroHash)
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