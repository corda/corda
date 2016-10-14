package com.r3corda.core.crypto

import com.r3corda.core.transactions.hashConcat
import java.util.*


class MerkleTreeException(val reason: String) : Exception() {
    override fun toString() = "Partial Merkle Tree exception. Reason: $reason"
}

//For convenient binary tree calculations.
fun <T: Number> log2(x: T): Double{
    return Math.log(x.toDouble())/Math.log(2.0)
}

/**
 * Building and verification of merkle branch. [branchHashes] - minimal set of hashes needed to check given subset of leaves.
 * [includeBranch] - path telling us how tree was traversed and which hashes are included in branchHashes.
 * [leavesSize] - number of all leaves in the original full Merkle tree.

 * If we include l2 in a PMT. includeBranch will be equal to: [], branchHashes will be the hashes of: [] TODO examples
 */
class PartialMerkleTree(
        val branchHashes: List<SecureHash>,
        val includeBranch: List<Boolean>,
        val treeHeight: Int,
        val leavesSize: Int
){
    companion object{
        private var hashIdx = 0 //Counters used in tree verification.
        private var includeIdx = 0

        /**
         * Builds new Partial Merkle Tree out of [allLeavesHashes]. [includeLeaves] is a list of Booleans that tells
         * which leaves from [allLeavesHashes] to include in a partial tree.
         */
        fun build(includeLeaves: List<Boolean>, allLeavesHashes: List<SecureHash>)
                : PartialMerkleTree {
            val branchHashes: MutableList<SecureHash> = ArrayList()
            val includeBranch: MutableList<Boolean> = ArrayList()
            val treeHeight = Math.ceil(log2(allLeavesHashes.size.toDouble())).toInt()
            whichNodesInBranch(treeHeight, 0, includeLeaves, allLeavesHashes, includeBranch, branchHashes)
            return PartialMerkleTree(branchHashes, includeBranch, treeHeight, allLeavesHashes.size)
        }

        /**
         * Recursively build a tree, traversal order - preorder.
         * [height] - height of the node in a tree (leaves are at 0 level).
         * [position] - position of the node at a given height level (starting from 0).
         * [includeBranch] - gives a path of traversal in a tree: false indicates that traversal stopped at given node
         * and it's hash is stored.
         * For true, algorithm continued to the subtree starting at that node (unless it reached leaves' level).
         * Hashes of leaves included in that partial tree are stored - that set is checked later during verification stage.
         */
        private fun whichNodesInBranch(
                height: Int,
                position: Int,
                includeLeaves: List<Boolean>,
                allLeavesHashes: List<SecureHash>,
                includeBranch: MutableList<Boolean>,
                resultHashes: MutableList<SecureHash>) {
            val isParent = checkIsParent(includeLeaves, height, position, allLeavesHashes.size)
            includeBranch.add(isParent)

            if (height == 0 || !isParent) {
                //Hash should be stored, don't traverse the subtree starting with that node.
                //Or height == 0 and recursion reached leaf level of the tree, hash is stored.
                resultHashes.add(treeHash(position, height, allLeavesHashes))
            } else {
                whichNodesInBranch(height - 1, position * 2, includeLeaves, allLeavesHashes, includeBranch, resultHashes)
                //If the tree is not full, we don't add the rightmost hash.
                if (position * 2 + 1 <= treeWidth(height-1, allLeavesHashes.size)-1) {
                    whichNodesInBranch(height - 1, position * 2 + 1, includeLeaves, allLeavesHashes, includeBranch, resultHashes)
                }
            }
        }

        /**
         *  Calculation of the node's hash using stack.
         *  Elements are pushed with an information about at what height they are in the tree.
         */
        private fun treeHash(position: Int, height: Int, allLeavesHashes: List<SecureHash>): SecureHash {
            var (startIdx, endIdx) = getNodeLeafRange(height, position, allLeavesHashes.size)
            val stack = Stack<Pair<Int, SecureHash>>()
            if (height == 0) { //Just return leaf's hash.
                return allLeavesHashes[position]
            }
            //Otherwise calculate hash from lower elements.
            while (true) {
                val size = stack.size
                //Two last elements on the stack are of the same height.
                //The way we build the stack hashes assures that they are siblings in a tree.
                if (size >= 2 && stack[size - 1].first == stack[size - 2].first) {
                    //Calculate hash of them and and push new node to the stack.
                    val el1 = stack.pop()
                    val el2 = stack.pop()
                    val h = el1.first
                    val combinedHash = el2.second.hashConcat(el1.second)
                    if (h + 1 == height) return combinedHash //We reached desired node.
                    else
                        stack.push(Pair(h + 1, combinedHash))
                } else if (startIdx > endIdx) { //Odd numbers of elements at that level.
                    stack.push(stack.last()) //Need to duplicate the last element.
                } else { //Add a leaf hash to the stack.
                    stack.push(Pair(0, allLeavesHashes[startIdx]))
                    startIdx++
                }
            }
        }

        //Calculates which leaves belong to the subtree starting from that node.
        private fun getNodeLeafRange(height: Int, position: Int, leavesCount: Int): Pair<Int, Int> {
            val offset = Math.pow(2.0, height.toDouble()).toInt()
            val start = position * offset
            val end = Math.min(start + offset - 1, leavesCount-1) //Not full binary tree.
            return Pair(start, end)
        }

        //Checks if a node at given height and position is a parent of some of the leaves that are included in the transaction.
        private fun checkIsParent(includeLeaves: List<Boolean>, height: Int, position: Int, leavesCount: Int): Boolean {
            val (start, end) = getNodeLeafRange(height, position, leavesCount)
            for (el in IntRange(start, end)) {
                if (includeLeaves[el]) return true
            }
            return false
        }

        //Return tree width at given height.
        private fun treeWidth(height: Int, leavesSize: Int): Double{
            return Math.ceil(leavesSize/Math.pow(2.0, height.toDouble()))
        }
    }

    /**
     * Verification that leavesHashes belong to this tree. It is leaves' ordering insensitive.
     * Checks if provided merkleRoot matches the one calculated from this Partial Merkle Tree.
     */
    fun verify(leavesHashes: List<SecureHash>, merkleRoot: SecureHash): Boolean{
        if(leavesSize==0) throw MerkleTreeException("PMT with zero leaves.")
        includeIdx = 0
        hashIdx = 0
        val hashesUsed = ArrayList<SecureHash>()
        val verifyRoot = verifyTree(treeHeight, 0, hashesUsed)
        if(includeIdx < includeBranch.size-1 || hashIdx < branchHashes.size -1)
            throw MerkleTreeException("Not all entries form PMT branch used.")
        //It means that we obtained more/less hashes than needed or different sets of hashes.
        //Ordering insensitive.
        if(leavesHashes.size != hashesUsed.size || leavesHashes.minus(hashesUsed).isNotEmpty())
            return false
        //Correctness of hashes is checked by folding the partial tree and comparing roots.
        return (verifyRoot == merkleRoot)
    }

    //Traverses the tree in the same order as it was built consuming includeBranch and branchHashes.
    private fun verifyTree(height: Int, position: Int, hashesUsed: MutableList<SecureHash>): SecureHash {
        if(includeIdx >= includeBranch.size)
            throw MerkleTreeException("Included nodes list index overflow.")
        val isParent = includeBranch[includeIdx]
        includeIdx++
        if (height == 0 || !isParent) { //Hash included in a branch was reached.
            if(hashIdx >branchHashes.size)
                throw MerkleTreeException("Branch hashes index overflow.")
            val hash = branchHashes[hashIdx]
            hashIdx++
            //It means that this leaf was included as part of original partial tree. It's hash is stored for later comparision.
            if(height == 0 && isParent)
                hashesUsed.add(hash)
            return hash
        } else { //Continue tree verification to left and right nodes and hash them together.
            val left: SecureHash = verifyTree(height - 1, position * 2, hashesUsed)
            val right: SecureHash = when{
                position * 2 + 1 < treeWidth(height-1, leavesSize) -> verifyTree(height - 1, position * 2 + 1, hashesUsed)
                else -> left
            }
            return left.hashConcat(right)
        }
    }
}
