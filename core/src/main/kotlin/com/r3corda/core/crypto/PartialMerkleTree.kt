package com.r3corda.core.crypto

import com.r3corda.core.transactions.hashConcat
import java.util.*


class MerkleTreeException(val reason: String) : Exception() {
    override fun toString() = "Partial Merkle Tree exception. Reason: $reason"
}

fun <T: Number> log2(x: T): Double{
    return Math.log(x.toDouble())/Math.log(2.0)
}

/*
Include branch:
    * false - hash stored, no hashes below stored
    * true - not stored, some hashes below stored
At leaves level, hashes of not included transaction's blocks are stored.
Tree traversal: preorder.
*/
class PartialMerkleTree(
        val branchHashes: List<SecureHash>,
        val includeBranch: List<Boolean>,
        val treeHeight: Int,
        val leavesSize: Int
){
    companion object{
        protected var hashIdx = 0
        protected var includeIdx = 0

        fun build(includeLeaves: List<Boolean>, allLeavesHashes: List<SecureHash>)
                : PartialMerkleTree {
            val branchHashes: MutableList<SecureHash> = ArrayList()
            val includeBranch: MutableList<Boolean> = ArrayList()
            val treeHeight = Math.ceil(log2(allLeavesHashes.size.toDouble())).toInt()
            whichNodesInBranch(treeHeight, 0, includeLeaves, allLeavesHashes, includeBranch, branchHashes)
            return PartialMerkleTree(branchHashes, includeBranch, treeHeight, allLeavesHashes.size)
        }

        //height - height of the node in the tree (leaves are 0)
        //position - position of the node at a given height level (starting from 0)
        fun whichNodesInBranch(
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
                resultHashes.add(treeHash(position, height, allLeavesHashes)) //resultHashes[height].add(treeHash)
            } else {
                whichNodesInBranch(height - 1, position * 2, includeLeaves, allLeavesHashes, includeBranch, resultHashes)
                //If the tree is not full, we don't add the rightmost hash.
                if (position * 2 + 1 <= treeWidth(height-1, allLeavesHashes.size)-1) {
                    whichNodesInBranch(height - 1, position * 2 + 1, includeLeaves, allLeavesHashes, includeBranch, resultHashes)
                }
            }
        }

        /* Calculation of the node's hash using stack.
        Pushes to the stack elements with an information about on what height they are in the tree.
        */
        fun treeHash(position: Int, height: Int, allLeavesHashes: List<SecureHash>): SecureHash {
            var (startIdx, endIdx) = getNodeLeafRange(height, position, allLeavesHashes.size)
            val stack = Stack<Pair<Int, SecureHash>>()
            if (height <= 0) { //Just return leaf's hash. todo if height < 0
                return allLeavesHashes[position]
            }
            //otherwise calculate
            while (true) {
                val size = stack.size
                //Two last elements on the stack are of the same height
                if (size >= 2 && stack[size - 1].first == stack[size - 2].first) {
                    //Calculate hash of them and and push new node to the stack.
                    val el1 = stack.pop()
                    val el2 = stack.pop()
                    val h = el1.first
                    val combinedHash = el2.second.hashConcat(el1.second)
                    if (h + 1 == height) return combinedHash //We reached desired node.
                    else
                        stack.push(Pair(h + 1, combinedHash))
                } else if (startIdx > endIdx) { //Odd numbers of elements at that level
                    stack.push(stack.last()) //Need to duplicate the last element. todo check
                } else { //Add a leaf hash to the stack
                    stack.push(Pair(0, allLeavesHashes[startIdx]))
                    startIdx++
                }
            }
        }

        //Calculates which leaves belong to the subtree starting from that node.
        //todo - out of tree width
        //OK
        protected fun getNodeLeafRange(height: Int, position: Int, leavesCount: Int): Pair<Int, Int> {
            val offset = Math.pow(2.0, height.toDouble()).toInt()
            val start = position * offset
            val end = Math.min(start + offset - 1, leavesCount-1) //Not full binary trees
            return Pair(start, end)
        }

        //Checks if a node at given height and position is a parent of some of the leaves that are included in the transaction.
        protected fun checkIsParent(includeLeaves: List<Boolean>, height: Int, position: Int, leavesCount: Int): Boolean {
            val (start, end) = getNodeLeafRange(height, position, leavesCount)
            for (el in IntRange(start, end)) {
                if (includeLeaves[el]) return true
            }
            return false
        }

        //OK
        protected fun treeWidth(height: Int, leavesSize: Int): Double{ //return tree width at given height
            return Math.ceil(leavesSize/Math.pow(2.0, height.toDouble()))
        }

    }

}
