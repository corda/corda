package com.r3corda.core.crypto

import com.r3corda.core.transactions.MerkleTree
import com.r3corda.core.transactions.hashConcat
import java.util.*


class MerkleTreeException(val reason: String): Exception() {
    override fun toString() = "Partial Merkle Tree exception. Reason: $reason"
}

/**
 * Building and verification of Partial Merkle Tree.
 * Partial Merkle Tree is a minimal tree needed to check that given set of leaves belongs to a full Merkle Tree.
 * todo example of partial tree
 */
class PartialMerkleTree(
    val root: PartialTree
) {
    /**
     * The structure is a little different than that of Merkle Tree.
     * Partial Tree by might not be a full binary tree. Leaves represent either original Merkle tree leaves
     * or cut subtree node with stored hash. We differentiate between the leaves that are included in a filtered
     * transaction and leaves that just keep hashes needed for calculation. Reason for this approach: during verification
     * it's easier to extract hashes used as base for this tree.
     */
    sealed class PartialTree() {
        class IncludedLeaf(val hash: SecureHash): PartialTree()
        class Leaf(val hash: SecureHash): PartialTree()
        class Node(val left: PartialTree, val right: PartialTree): PartialTree()
    }

    companion object {
        /**
         * @param merkleRoot
         * @param includeHashes
         * @return Partial Merkle tree root.
         */
        fun build(merkleRoot: MerkleTree, includeHashes: List<SecureHash>): PartialMerkleTree {
            val usedHashes = ArrayList<SecureHash>()
            //Too much included hashes or different ones.
            val tree = buildPartialTree(merkleRoot, includeHashes, usedHashes)
            if(includeHashes.size != usedHashes.size)
                throw MerkleTreeException("Some of the provided hashes are not in the tree.")
            return PartialMerkleTree(tree.second)
        }

        /**
         * @param root Root of full Merkle tree which is a base for a partial one.
         * @param includeHashes Hashes of leaves to be included in this partial tree.
         * @param usedHashes Hashes actually used to build this partial tree.
         * @return Pair, first element indicates if in a subtree there is a leaf that is included in that partial tree.
         * Second element refers to that subtree.
         */
        private fun buildPartialTree(
                root: MerkleTree,
                includeHashes: List<SecureHash>,
                usedHashes: MutableList<SecureHash>
        ): Pair<Boolean, PartialTree> {
            if (root is MerkleTree.Leaf) {
                if (root.value in includeHashes) {
                    usedHashes.add(root.value)
                    return Pair(true, PartialTree.IncludedLeaf(root.value))
                } else return Pair(false, PartialTree.Leaf(root.value))
            } else if (root is MerkleTree.DuplicatedLeaf) {
                //Duplicate leaves should be stored as normal leaves not included ones.
                return Pair(false, PartialTree.Leaf(root.value))
            } else if (root is MerkleTree.Node) {
                val leftNode = buildPartialTree(root.left, includeHashes, usedHashes)
                val rightNode = buildPartialTree(root.right, includeHashes, usedHashes)
                if (leftNode.first or rightNode.first) {
                    //This node is on a path to some included leaves. Don't store hash.
                    val newTree = PartialTree.Node(leftNode.second, rightNode.second)
                    return Pair(true, newTree)
                } else {
                    //This node has no included leaves below. Cut the tree here and store a hash as a Leaf.
                    val newTree = PartialTree.Leaf(root.value)
                    return Pair(false, newTree)
                }
            } else {
                throw MerkleTreeException("Invalid MerkleTree.")
            }
        }
    }

    /**
     * @param merkleRootHash 
     * @param hashesToCheck
     */
    fun verify(merkleRootHash: SecureHash, hashesToCheck: List<SecureHash>): Boolean {
        val usedHashes = ArrayList<SecureHash>()
        val verifyRoot = verify(root, usedHashes)
        //It means that we obtained more/less hashes than needed or different sets of hashes.
        //Ordering insensitive.
        if(hashesToCheck.size != usedHashes.size || hashesToCheck.minus(usedHashes).isNotEmpty())
            return false
        return (verifyRoot == merkleRootHash)
    }

    /**
     * Recursive calculation of root of this partial tree.
     * Modifies usedHashes to later check for inclusion with hashes provided.
     */
    private fun verify(node: PartialTree, usedHashes: MutableList<SecureHash>): SecureHash{
        if (node is PartialTree.IncludedLeaf) {
            usedHashes.add(node.hash)
            return node.hash
        } else if (node is PartialTree.Leaf ) {
            return node.hash
        } else if (node is PartialTree.Node){
            val leftHash = verify(node.left, usedHashes)
            val rightHash = verify(node.right, usedHashes)
            return leftHash.hashConcat(rightHash)
        } else {
            throw MerkleTreeException("Invalid node type.")
        }
    }
}
