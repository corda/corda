package net.corda.core.transactions

import net.corda.core.contracts.*
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.keys
import net.corda.core.identity.Party
import net.corda.core.internal.Emoji
import net.corda.core.node.ServicesForResolution
import java.security.PublicKey
import java.security.SignatureException
import java.util.function.Predicate

/**
 * A transaction ready for serialisation, without any signatures attached. A WireTransaction is usually wrapped
 * by a [SignedTransaction] that carries the signatures over this payload.
 * The identity of the transaction is the Merkle tree root of its components (see [MerkleTree]).
 */
data class WireTransaction(
        /** Pointers to the input states on the ledger, identified by (tx identity hash, output index). */
        override val inputs: List<StateRef>,
        /** Hashes of the ZIP/JAR files that are needed to interpret the contents of this wire transaction. */
        override val attachments: List<SecureHash>,
        override val outputs: List<TransactionState<ContractState>>,
        /** Ordered list of ([CommandData], [PublicKey]) pairs that instruct the contracts what to do. */
        override val commands: List<Command<*>>,
        override val notary: Party?,
        override val timeWindow: TimeWindow?,
        override val privacySalt: PrivacySalt = PrivacySalt()
) : CoreTransaction(), TraversableTransaction {
    init {
        checkBaseInvariants()
        if (timeWindow != null) check(notary != null) { "Transactions with time-windows must be notarised" }
        check(availableComponents.isNotEmpty()) { "A WireTransaction cannot be empty" }
    }

    /** The transaction id is represented by the root hash of Merkle tree over the transaction components. */
    override val id: SecureHash get() = merkleTree.hash

    /** Public keys that need to be fulfilled by signatures in order for the transaction to be valid. */
    val requiredSigningKeys: Set<PublicKey> get() {
        val commandKeys = commands.flatMap { it.signers }.toSet()
        // TODO: prevent notary field from being set if there are no inputs and no timestamp
        return if (notary != null && (inputs.isNotEmpty() || timeWindow != null)) {
            commandKeys + notary.owningKey
        } else {
            commandKeys
        }
    }

    /**
     * Looks up identities and attachments from storage to generate a [LedgerTransaction]. A transaction is expected to
     * have been fully resolved using the resolution flow by this point.
     *
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     */
    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServicesForResolution): LedgerTransaction {
        return toLedgerTransaction(
                resolveIdentity = { services.identityService.partyFromKey(it) },
                resolveAttachment = { services.attachments.openAttachment(it) },
                resolveStateRef = { services.loadState(it) }
        )
    }

    /**
     * Looks up identities, attachments and dependent input states using the provided lookup functions in order to
     * construct a [LedgerTransaction]. Note that identity lookup failure does *not* cause an exception to be thrown.
     *
     * @throws AttachmentResolutionException if a required attachment was not found using [resolveAttachment].
     * @throws TransactionResolutionException if an input was not found not using [resolveStateRef].
     */
    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(
            resolveIdentity: (PublicKey) -> Party?,
            resolveAttachment: (SecureHash) -> Attachment?,
            resolveStateRef: (StateRef) -> TransactionState<*>?
    ): LedgerTransaction {
        // Look up public keys to authenticated identities. This is just a stub placeholder and will all change in future.
        val authenticatedArgs = commands.map {
            val parties = it.signers.mapNotNull { pk -> resolveIdentity(pk) }
            AuthenticatedObject(it.signers, parties, it.value)
        }
        // Open attachments specified in this transaction. If we haven't downloaded them, we fail.
        val attachments = attachments.map { resolveAttachment(it) ?: throw AttachmentResolutionException(it) }
        val resolvedInputs = inputs.map { ref ->
            resolveStateRef(ref)?.let { StateAndRef(it, ref) } ?: throw TransactionResolutionException(ref.txhash)
        }
        return LedgerTransaction(resolvedInputs, outputs, authenticatedArgs, attachments, id, notary, timeWindow, privacySalt)
    }

    /**
     * Build filtered transaction using provided filtering functions.
     */
    fun buildFilteredTransaction(filtering: Predicate<Any>): FilteredTransaction {
        return FilteredTransaction.buildMerkleTransaction(this, filtering)
    }

    /**
     * Builds whole Merkle tree for a transaction. If input states exist, the first leaf of this tree is
     * the root hash of [inputsMerkleTree].
     */
    val merkleTree: MerkleTree by lazy { fullMerkleTree() }

    /**
     * Builds the input states sub Merkle tree. This is required so non-validated notaries can see
     * the number of all inputs in a transaction.
     */
    val inputsMerkleTree: MerkleTree? by lazy { inputStatesMerkleTree() }

    private fun fullMerkleTree(): MerkleTree {
        return if (!inputs.isEmpty()) {
            // Use the root hash of the inputs sub Merkle Tree as first leaf in the whole Merkle tree.
            MerkleTree.getMerkleTree(listOf(inputsMerkleTree!!.hash) + availableComponentHashes.subList(inputs.size, availableComponentHashes.size))
        } else {
            // If there are no input states, add the oneHash as the leftmost leaf to avoid a certain security issue,
            // where one can trick the non-validating notary by hiding all input states.
            // OneHash was used instead of zeroHash to avoid confusion
            MerkleTree.getMerkleTree(listOf(SecureHash.oneHash) + availableComponentHashes)
        }
    }

    // Compute the input states sub Merkle Tree.
    private fun inputStatesMerkleTree(): MerkleTree? {
        return if (!inputs.isEmpty()) {
            MerkleTree.getMerkleTree(availableComponentHashes.subList(0, inputs.size))
        } else {
            null
        }
    }

    /**
     * Construction of partial transaction from WireTransaction based on filtering.
     * Note that list of nonces to be sent is updated on the fly, based on the index of the filtered tx component.
     * @param filtering filtering over the whole WireTransaction
     * @returns FilteredLeaves used in PartialMerkleTree calculation and verification.
     */
    fun filterWithFun(filtering: Predicate<Any>): FilteredLeaves {
        val nonces: MutableList<SecureHash> = mutableListOf()
        val offsets = indexOffsets()
        fun notNullFalseAndNoncesUpdate(elem: Any?, index: Int): Any? {
            return if (elem == null || !filtering.test(elem)) {
                null
            } else {
                nonces.add(computeNonce(privacySalt, index))
                elem
            }
        }

        fun <T : Any> filterAndNoncesUpdate(t: T, index: Int): Boolean {
            return if (filtering.test(t)) {
                nonces.add(computeNonce(privacySalt, index))
                true
            } else {
                false
            }
        }

        // TODO: We should have a warning (require) if all leaves (excluding salt) are visible after filtering.
        //      Consider the above after refactoring FilteredTransaction to implement TraversableTransaction,
        //      so that a WireTransaction can be used when required to send a full tx (e.g. RatesFixFlow in Oracles).
        return FilteredLeaves(
                inputs.filterIndexed { index, it -> filterAndNoncesUpdate(it, index) },
                attachments.filterIndexed { index, it -> filterAndNoncesUpdate(it, index + offsets[0]) },
                outputs.filterIndexed { index, it -> filterAndNoncesUpdate(it, index + offsets[1]) },
                commands.filterIndexed { index, it -> filterAndNoncesUpdate(it, index + offsets[2]) },
                notNullFalseAndNoncesUpdate(notary, offsets[3]) as Party?,
                notNullFalseAndNoncesUpdate(timeWindow, offsets[4]) as TimeWindow?,
                nonces
        )
    }

    // We use index offsets, to get the actual leaf-index per transaction component required for nonce computation.
    private fun indexOffsets(): List<Int> {
        // There is no need to add an index offset for inputs, because they are the first components in the
        // transaction format and it is always zero. Thus, offsets[0] corresponds to attachments,
        // offsets[1] to outputs, offsets[2] to commands and so on.
        val offsets = mutableListOf(inputs.size, inputs.size + attachments.size)
        offsets.add(offsets.last() + outputs.size)
        offsets.add(offsets.last() + commands.size)
        if (notary != null) {
            offsets.add(offsets.last() + 1)
        } else {
            offsets.add(offsets.last())
        }
        if (timeWindow != null) {
            offsets.add(offsets.last() + 1)
        } else {
            offsets.add(offsets.last())
        }
        // No need to add offset for privacySalt as it doesn't require a nonce.
        return offsets
    }

    /**
     * Checks that the given signature matches one of the commands and that it is a correct signature over the tx.
     *
     * @throws SignatureException if the signature didn't match the transaction contents.
     * @throws IllegalArgumentException if the signature key doesn't appear in any command.
     */
    fun checkSignature(sig: TransactionSignature) {
        require(commands.any { it.signers.any { sig.by in it.keys } }) { "Signature key doesn't match any command" }
        sig.verify(id)
    }

    override fun toString(): String {
        val buf = StringBuilder()
        buf.appendln("Transaction:")
        for (input in inputs) buf.appendln("${Emoji.rightArrow}INPUT:      $input")
        for ((data) in outputs) buf.appendln("${Emoji.leftArrow}OUTPUT:     $data")
        for (command in commands) buf.appendln("${Emoji.diamond}COMMAND:    $command")
        for (attachment in attachments) buf.appendln("${Emoji.paperclip}ATTACHMENT: $attachment")
        return buf.toString()
    }
}
