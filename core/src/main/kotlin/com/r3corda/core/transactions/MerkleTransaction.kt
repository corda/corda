package com.r3corda.core.transactions

import com.r3corda.core.contracts.Command
import com.r3corda.core.crypto.PartialMerkleTree
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.sha256
import com.r3corda.core.serialization.serialize
import java.util.*

/* Creation and verification of a Merkle Tree for a Wire Transaction
* Tree should be the same no matter the ordering of outputs, inputs, attachments and commands. */

/* Transaction is split into following blocks:
inputs, outputs, commands, attachments' refs
If a row in a tree has odd number of elements - the final hash is hashed with itself.
*/

fun SecureHash.hashConcat(other: SecureHash) = (this.bits + other.bits).sha256()

class MerkleTransaction(
        val merkleRoot: SecureHash, //todo that should be in a wire tx? not with PMT and filtered commands
        val filteredCommands : List<Command>, //todo + <Command> do we want to also filter something else than commands?
        val partialMerkleTree: PartialMerkleTree
){
    companion object {
        fun buildMerkleTransaction(wtx: WireTransaction, filterFunction: (Command) -> Boolean): MerkleTransaction {
            val merkleTree: List<SecureHash> = buildMerkleTree(wtx) //todo change
            val merkleRoot = merkleTree.last()

            val allLeavesHashes: MutableList<SecureHash> = ArrayList()
            getTransactionBlocks(wtx).mapTo(allLeavesHashes, { it.sha256() })
            val filteredCommands: MutableList<Command> = ArrayList()
            val includeLeaves: MutableList<Boolean> = ArrayList()
            wtx.commands.forEach {
                val include = filterFunction(it)
                if(include) filteredCommands.add(it)
                includeLeaves.add(include)
            }

            val pmt = PartialMerkleTree.build(includeLeaves, allLeavesHashes)
            return MerkleTransaction(merkleRoot, filteredCommands, pmt)
        }

        /* Function that splits the transaction into serialized blocks.
        Blocks: inputs, outputs, attachments, commands. */
        private fun getTransactionBlocks(wtx: WireTransaction) : MutableList<ByteArray> {
            val blocks: MutableList<ByteArray> = ArrayList()
            val toBlockList = listOf(wtx.inputs, wtx.outputs, wtx.attachments, wtx.commands) //todo ordering
            toBlockList.flatMapTo(blocks, { listOf(it.serialize().bits) } )
            return blocks
        }

        /* Start building a Merkle tree from the transaction.
        Calls helper tailrecursive function with an accumulator and initial hashedBlocks */
        fun buildMerkleTree(wtx: WireTransaction): MutableList<SecureHash>{
            val blocks = getTransactionBlocks(wtx)
            val hashedBlocks: MutableList<SecureHash> = ArrayList()
            blocks.mapTo(hashedBlocks, { it.sha256() })
            val merkleTreeList = ArrayList<SecureHash>()
            merkleTreeList.addAll(hashedBlocks)
            buildMerkleTree(merkleTreeList, hashedBlocks)
            return merkleTreeList
        }

        tailrec fun buildMerkleTree(
                resultHashes: MutableList<SecureHash>,
                lastHashList: List<SecureHash>){
            if(lastHashList.size <= 1) {
                return
            }
            else{
                val newLevelHashes: MutableList<SecureHash> = ArrayList()
                var i = 0
                while(i < lastHashList.size){
                    val left = lastHashList[i]
                    //If there is an odd number of elements, the last element is hashed with itself
                    val right = lastHashList[Math.min(i+1, lastHashList.size - 1)]
                    val combined = left.hashConcat(right)
                    resultHashes.add(combined)
                    newLevelHashes.add(combined)
                    i+=2
                }
                buildMerkleTree(resultHashes, newLevelHashes)
            }
        }
    }

    //todo exception
    fun verify():Boolean{
        val hashes: List<SecureHash> = filteredCommands.map { it.serialize().sha256() }
        return partialMerkleTree.verify(hashes, merkleRoot)
    }
}
