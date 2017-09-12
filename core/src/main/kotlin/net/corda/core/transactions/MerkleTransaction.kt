package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import java.nio.ByteBuffer
import java.util.function.Predicate

/**
 * If a privacy salt is provided, the resulted output (Merkle-leaf) is computed as
 * Hash(serializedObject || Hash(privacy_salt || obj_index_in_merkle_tree)).
 */
fun <T : Any> serializedHash(x: T, privacySalt: PrivacySalt?, index: Int): SecureHash {
    return if (privacySalt != null)
        serializedHash(x, computeNonce(privacySalt, index))
    else
        serializedHash(x)
}

fun <T : Any> serializedHash(x: T, nonce: SecureHash): SecureHash {
    return if (x !is PrivacySalt) // PrivacySalt is not required to have an accompanied nonce.
        (x.serialize(context = SerializationFactory.defaultFactory.defaultContext.withoutReferences()).bytes + nonce.bytes).sha256()
    else
        serializedHash(x)
}

fun <T : Any> serializedHash(x: T): SecureHash = x.serialize(context = SerializationFactory.defaultFactory.defaultContext.withoutReferences()).bytes.sha256()

/** The nonce is computed as Hash(privacySalt || index). */
fun computeNonce(privacySalt: PrivacySalt, index: Int) = (privacySalt.bytes + ByteBuffer.allocate(4).putInt(index).array()).sha256()

/**
 * Implemented by [WireTransaction] and [FilteredLeaves]. A TraversableTransaction allows you to iterate
 * over the flattened components of the underlying transaction structure, taking into account that some
 * may be missing in the case of this representing a "torn" transaction. Please see the user guide section
 * "Transaction tear-offs" to learn more about this feature.
 *
 * The [availableComponents] property is used for calculation of the transaction's [MerkleTree], which is in
 * turn used to derive the ID hash.
 */
interface TraversableTransaction {
    val inputs: List<StateRef>
    val attachments: List<SecureHash>
    val outputs: List<TransactionState<ContractState>>
    val commands: List<Command<*>>
    val notary: Party?
    val timeWindow: TimeWindow?
    /**
     * For privacy purposes, each part of a transaction should be accompanied by a nonce.
     * To avoid storing a random number (nonce) per component, an initial "salt" is the sole value utilised,
     * so that all component nonces are deterministically computed in the following way:
     * nonce1 = H(salt || 1)
     * nonce2 = H(salt || 2)
     *
     * Thus, all of the nonces are "independent" in the sense that knowing one or some of them, you can learn
     * nothing about the rest.
     */
    val privacySalt: PrivacySalt?

    /**
     * Returns a flattened list of all the components that are present in the transaction, in the following order:
     *
     * - Each input that is present
     * - Each attachment that is present
     * - Each output that is present
     * - Each command that is present
     * - The notary [Party], if present
     * - The time-window of the transaction, if present
     * - The privacy salt required for nonces, always presented in [WireTransaction] and always null in [FilteredLeaves]
     */
    val availableComponents: List<Any>
        // NOTE: if the order below is altered or components are added/removed in the future, one should also reflect
        //      this change to the indexOffsets() method in WireTransaction.
        get() {
            // We may want to specify our own behaviour on certain tx fields.
            // Like if we include them at all, what to do with null values, if we treat list as one or not etc. for building
            // torn-off transaction and id calculation.
            val result = mutableListOf(inputs, attachments, outputs, commands).flatten().toMutableList()
            notary?.let { result += it }
            timeWindow?.let { result += it }
            privacySalt?.let { result += it }
            return result
        }

    /**
     * Calculate the hashes of the sub-components of the transaction, that are used to build its Merkle tree.
     * The root of the tree is the transaction identifier. The tree structure is helpful for privacy, please
     * see the user-guide section "Transaction tear-offs" to learn more about this topic.
     */
    val availableComponentHashes: List<SecureHash> get() = availableComponents.mapIndexed { index, it -> serializedHash(it, privacySalt, index) }
}

/**
 * Class that holds filtered leaves for a partial Merkle transaction. We assume mixed leaf types, notice that every
 * field from [WireTransaction] can be used in [PartialMerkleTree] calculation, except for the privacySalt.
 * A list of nonces is also required to (re)construct component hashes.
 */
@CordaSerializable
class FilteredLeaves(
        override val inputs: List<StateRef>,
        override val attachments: List<SecureHash>,
        override val outputs: List<TransactionState<ContractState>>,
        override val commands: List<Command<*>>,
        override val notary: Party?,
        override val timeWindow: TimeWindow?,
        val nonces: List<SecureHash>
) : TraversableTransaction {

    /**
     * PrivacySalt should be always null for FilteredLeaves, because making it accidentally visible would expose all
     * nonces (including filtered out components) causing privacy issues, see [serializedHash] and
     * [TraversableTransaction.privacySalt].
     */
    override val privacySalt: PrivacySalt? get() = null

    init {
        require(availableComponents.size == nonces.size) { "Each visible component should be accompanied by a nonce." }
    }

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
        val checkList = availableComponents.map { checkingFun(it) }
        return (!checkList.isEmpty()) && checkList.all { it }
    }

    override val availableComponentHashes: List<SecureHash> get() = availableComponents.mapIndexed { index, it -> serializedHash(it, nonces[index]) }
}

/**
 * Class representing merkleized filtered transaction.
 * @param id Merkle tree root hash.
 * @param filteredLeaves Leaves included in a filtered transaction.
 * @param partialMerkleTree Merkle branch needed to verify filteredLeaves.
 */
@CordaSerializable
class FilteredTransaction private constructor(
        val id: SecureHash,
        val filteredLeaves: FilteredLeaves,
        val partialMerkleTree: PartialMerkleTree
) {
    companion object {
        /**
         * Construction of filtered transaction with Partial Merkle Tree.
         * @param wtx WireTransaction to be filtered.
         * @param filtering filtering over the whole WireTransaction
         */
        @JvmStatic
        fun buildMerkleTransaction(wtx: WireTransaction,
                                   filtering: Predicate<Any>
        ): FilteredTransaction {
            val filteredLeaves = filterWithFun(wtx, filtering)
            val merkleTree = wtx.merkleTree
            val pmt = PartialMerkleTree.build(merkleTree, filteredLeaves.availableComponentHashes)
            return FilteredTransaction(merkleTree.hash, filteredLeaves, pmt)
        }

        /**
         * Construction of partial transaction from WireTransaction based on filtering.
         * Note that list of nonces to be sent is updated on the fly, based on the index of the filtered tx component.
         * @param filtering filtering over the whole WireTransaction
         * @returns FilteredLeaves used in PartialMerkleTree calculation and verification.
         */
        private fun filterWithFun(wtx: WireTransaction, filtering: Predicate<Any>): FilteredLeaves {
            val nonces: MutableList<SecureHash> = mutableListOf()
            val offsets = indexOffsets(wtx)
            fun notNullFalseAndNoncesUpdate(elem: Any?, index: Int): Any? {
                return if (elem == null || !filtering.test(elem)) {
                    null
                } else {
                    nonces.add(computeNonce(wtx.privacySalt, index))
                    elem
                }
            }

            fun <T : Any> filterAndNoncesUpdate(t: T, index: Int): Boolean {
                return if (filtering.test(t)) {
                    nonces.add(computeNonce(wtx.privacySalt, index))
                    true
                } else {
                    false
                }
            }

            // TODO: We should have a warning (require) if all leaves (excluding salt) are visible after filtering.
            //      Consider the above after refactoring FilteredTransaction to implement TraversableTransaction,
            //      so that a WireTransaction can be used when required to send a full tx (e.g. RatesFixFlow in Oracles).
            return FilteredLeaves(
                    wtx.inputs.filterIndexed { index, it -> filterAndNoncesUpdate(it, index) },
                    wtx.attachments.filterIndexed { index, it -> filterAndNoncesUpdate(it, index + offsets[0]) },
                    wtx.outputs.filterIndexed { index, it -> filterAndNoncesUpdate(it, index + offsets[1]) },
                    wtx.commands.filterIndexed { index, it -> filterAndNoncesUpdate(it, index + offsets[2]) },
                    notNullFalseAndNoncesUpdate(wtx.notary, offsets[3]) as Party?,
                    notNullFalseAndNoncesUpdate(wtx.timeWindow, offsets[4]) as TimeWindow?,
                    nonces
            )
        }

        // We use index offsets, to get the actual leaf-index per transaction component required for nonce computation.
        private fun indexOffsets(wtx: WireTransaction): List<Int> {
            // There is no need to add an index offset for inputs, because they are the first components in the
            // transaction format and it is always zero. Thus, offsets[0] corresponds to attachments,
            // offsets[1] to outputs, offsets[2] to commands and so on.
            val offsets = mutableListOf(wtx.inputs.size, wtx.inputs.size + wtx.attachments.size)
            offsets.add(offsets.last() + wtx.outputs.size)
            offsets.add(offsets.last() + wtx.commands.size)
            if (wtx.notary != null) {
                offsets.add(offsets.last() + 1)
            } else {
                offsets.add(offsets.last())
            }
            if (wtx.timeWindow != null) {
                offsets.add(offsets.last() + 1)
            } else {
                offsets.add(offsets.last())
            }
            // No need to add offset for privacySalt as it doesn't require a nonce.
            return offsets
        }
    }

    /**
     * Runs verification of partial Merkle branch against [id].
     */
    @Throws(MerkleTreeException::class)
    fun verify(): Boolean {
        val hashes: List<SecureHash> = filteredLeaves.availableComponentHashes
        if (hashes.isEmpty())
            throw MerkleTreeException("Transaction without included leaves.")
        return partialMerkleTree.verify(id, hashes)
    }
}
