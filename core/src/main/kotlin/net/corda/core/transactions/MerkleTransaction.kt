package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults.P2P_CONTEXT
import net.corda.core.serialization.serialize
import java.nio.ByteBuffer
import java.util.function.Predicate

/**
 * If a privacy salt is provided, the resulted output (merkle-leaf) is computed as
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
        (x.serialize(context = P2P_CONTEXT.withoutReferences()).bytes + nonce.bytes).sha256()
    else
        serializedHash(x)
}

fun <T : Any> serializedHash(x: T): SecureHash = x.serialize(context = P2P_CONTEXT.withoutReferences()).bytes.sha256()

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
    val type: TransactionType?
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
     * - The type of the transaction, if present
     * - The time-window of the transaction, if present
     * - The privacy salt required for nonces, always presented in [WireTransaction] and always null in [FilteredLeaves]
     */
    val availableComponents: List<Any>
        get() {
            // We may want to specify our own behaviour on certain tx fields.
            // Like if we include them at all, what to do with null values, if we treat list as one or not etc. for building
            // torn-off transaction and id calculation.
            val result = mutableListOf(inputs, attachments, outputs, commands).flatten().toMutableList()
            notary?.let { result += it }
            type?.let { result += it }
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
        override val type: TransactionType?,
        override val timeWindow: TimeWindow?,
        val nonces: List<SecureHash>
) : TraversableTransaction {

    override val privacySalt: PrivacySalt? get() = null

    init {
        require(availableComponents.size == nonces.size)
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
 * @param rootHash Merkle tree root hash.
 * @param filteredLeaves Leaves included in a filtered transaction.
 * @param partialMerkleTree Merkle branch needed to verify filteredLeaves.
 */
@CordaSerializable
class FilteredTransaction private constructor(
        val rootHash: SecureHash,
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
            val filteredLeaves = wtx.filterWithFun(filtering)
            val merkleTree = wtx.merkleTree
            val pmt = PartialMerkleTree.build(merkleTree, filteredLeaves.availableComponentHashes)
            return FilteredTransaction(merkleTree.hash, filteredLeaves, pmt)
        }
    }

    /**
     * Runs verification of Partial Merkle Branch against [rootHash].
     */
    @Throws(MerkleTreeException::class)
    fun verify(): Boolean {
        val hashes: List<SecureHash> = filteredLeaves.availableComponentHashes
        if (hashes.isEmpty())
            throw MerkleTreeException("Transaction without included leaves.")
        return partialMerkleTree.verify(rootHash, hashes)
    }
}
