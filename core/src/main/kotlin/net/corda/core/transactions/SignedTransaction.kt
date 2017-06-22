package net.corda.core.transactions

import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.NamedByHash
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import java.security.PublicKey
import java.security.SignatureException
import java.util.*

/**
 * SignedTransaction wraps a serialized WireTransaction. It contains one or more signatures, each one for
 * a public key (including composite keys) that is mentioned inside a transaction command. SignedTransaction is the top level transaction type
 * and the type most frequently passed around the network and stored. The identity of a transaction is the hash of Merkle root
 * of a WireTransaction, therefore if you are storing data keyed by WT hash be aware that multiple different STs may
 * map to the same key (and they could be different in important ways, like validity!). The signatures on a
 * SignedTransaction might be invalid or missing: the type does not imply validity.
 * A transaction ID should be the hash of the [WireTransaction] Merkle tree root. Thus adding or removing a signature does not change it.
 *
 * @param sigs a list of signatures from individual (non-composite) public keys. This is passed as a list of signatures
 * when verifying composite key signatures, but may be used as individual signatures where a single key is expected to
 * sign.
 */
// DOCSTART 1
data class SignedTransaction(val txBits: SerializedBytes<WireTransaction>,
                             val sigs: List<DigitalSignature.WithKey>
) : NamedByHash {
// DOCEND 1
    init {
        require(sigs.isNotEmpty())
    }

    // TODO: This needs to be reworked to ensure that the inner WireTransaction is only ever deserialised sandboxed.

    /** Lazily calculated access to the deserialised/hashed transaction data. */
    val tx: WireTransaction by lazy { WireTransaction.deserialize(txBits) }

    /**
     * The Merkle root of the inner [WireTransaction]. Note that this is _not_ the same as the simple hash of
     * [txBits], which would not use the Merkle tree structure. If the difference isn't clear, please consult
     * the user guide section "Transaction tear-offs" to learn more about Merkle trees.
     */
    override val id: SecureHash get() = tx.id

    @CordaSerializable
    class SignaturesMissingException(val missing: Set<PublicKey>, val descriptions: List<String>, override val id: SecureHash) : NamedByHash, SignatureException() {
        override fun toString(): String {
            return "Missing signatures for $descriptions on transaction ${id.prefixChars()} for ${missing.joinToString()}"
        }
    }

    /**
     * Verifies the signatures on this transaction and throws if any are missing which aren't passed as parameters.
     * In this context, "verifying" means checking they are valid signatures and that their public keys are in
     * the contained transactions [BaseTransaction.mustSign] property.
     *
     * Normally you would not provide any keys to this function, but if you're in the process of building a partial
     * transaction and you want to access the contents before you've signed it, you can specify your own keys here
     * to bypass that check.
     *
     * @throws SignatureException if any signatures are invalid or unrecognised.
     * @throws SignaturesMissingException if any signatures should have been present but were not.
     */
    // DOCSTART 2
    @Throws(SignatureException::class)
    fun verifySignatures(vararg allowedToBeMissing: PublicKey): WireTransaction {
    // DOCEND 2
        // Embedded WireTransaction is not deserialised until after we check the signatures.
        checkSignaturesAreValid()

        val missing = getMissingSignatures()
        if (missing.isNotEmpty()) {
            val allowed = allowedToBeMissing.toSet()
            val needed = missing - allowed
            if (needed.isNotEmpty())
                throw SignaturesMissingException(needed, getMissingKeyDescriptions(needed), id)
        }
        check(tx.id == id)
        return tx
    }

    /**
     * Mathematically validates the signatures that are present on this transaction. This does not imply that
     * the signatures are by the right keys, or that there are sufficient signatures, just that they aren't
     * corrupt. If you use this function directly you'll need to do the other checks yourself. Probably you
     * want [verifySignatures] instead.
     *
     * @throws SignatureException if a signature fails to verify.
     */
    @Throws(SignatureException::class)
    fun checkSignaturesAreValid() {
        for (sig in sigs) {
            sig.verify(id.bytes)
        }
    }

    private fun getMissingSignatures(): Set<PublicKey> {
        val sigKeys = sigs.map { it.by }.toSet()
        // TODO Problem is that we can get single PublicKey wrapped as CompositeKey in allowedToBeMissing/mustSign
        //  equals on CompositeKey won't catch this case (do we want to single PublicKey be equal to the same key wrapped in CompositeKey with threshold 1?)
        val missing = tx.mustSign.filter { !it.isFulfilledBy(sigKeys) }.toSet()
        return missing
    }

    /**
     * Get a human readable description of where signatures are required from, and are missing, to assist in debugging
     * the underlying cause.
     */
    private fun getMissingKeyDescriptions(missing: Set<PublicKey>): ArrayList<String> {
        // TODO: We need a much better way of structuring this data
        val missingElements = ArrayList<String>()
        this.tx.commands.forEach { command ->
            if (command.signers.any { it in missing })
                missingElements.add(command.toString())
        }
        if (this.tx.notary?.owningKey in missing)
            missingElements.add("notary")
        return missingElements
    }

    /** Returns the same transaction but with an additional (unchecked) signature. */
    fun withAdditionalSignature(sig: DigitalSignature.WithKey) = copy(sigs = sigs + sig)

    /** Returns the same transaction but with an additional (unchecked) signatures. */
    fun withAdditionalSignatures(sigList: Iterable<DigitalSignature.WithKey>) = copy(sigs = sigs + sigList)

    /** Alias for [withAdditionalSignature] to let you use Kotlin operator overloading. */
    operator fun plus(sig: DigitalSignature.WithKey) = withAdditionalSignature(sig)

    /** Alias for [withAdditionalSignatures] to let you use Kotlin operator overloading. */
    operator fun plus(sigList: Collection<DigitalSignature.WithKey>) = withAdditionalSignatures(sigList)

    /**
     * Checks the transaction's signatures are valid, optionally calls [verifySignatures] to check
     * all required signatures are present, and then calls [WireTransaction.toLedgerTransaction]
     * with the passed in [ServiceHub] to resolve the dependencies, returning an unverified
     * LedgerTransaction.
     *
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     * @throws SignatureException if any signatures were invalid or unrecognised
     * @throws SignaturesMissingException if any signatures that should have been present are missing.
     */
    @JvmOverloads
    @Throws(SignatureException::class, AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServiceHub, checkSufficientSignatures: Boolean = true): LedgerTransaction {
        checkSignaturesAreValid()
        if (checkSufficientSignatures) verifySignatures()
        return tx.toLedgerTransaction(services)
    }

    /**
     * Checks the transaction's signatures are valid, optionally calls [verifySignatures] to check
     * all required signatures are present, calls [WireTransaction.toLedgerTransaction] with the
     * passed in [ServiceHub] to resolve the dependencies and return an unverified
     * LedgerTransaction, then verifies the LedgerTransaction.
     *
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     * @throws SignatureException if any signatures were invalid or unrecognised
     * @throws SignaturesMissingException if any signatures that should have been present are missing.
     */
    @JvmOverloads
    @Throws(SignatureException::class, AttachmentResolutionException::class, TransactionResolutionException::class, TransactionVerificationException::class)
    fun verify(services: ServiceHub, checkSufficientSignatures: Boolean = true) {
        checkSignaturesAreValid()
        if (checkSufficientSignatures) verifySignatures()
        tx.toLedgerTransaction(services).verify()
    }

    override fun toString(): String = "${javaClass.simpleName}(id=$id)"
}
