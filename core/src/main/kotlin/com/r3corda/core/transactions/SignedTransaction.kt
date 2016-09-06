package com.r3corda.core.transactions

import com.r3corda.core.contracts.NamedByHash
import com.r3corda.core.contracts.TransactionResolutionException
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.toStringsShort
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.serialization.SerializedBytes
import java.io.FileNotFoundException
import java.security.PublicKey
import java.security.SignatureException
import java.util.*

/**
 * SignedTransaction wraps a serialized WireTransaction. It contains one or more signatures, each one for
 * a public key that is mentioned inside a transaction command. SignedTransaction is the top level transaction type
 * and the type most frequently passed around the network and stored. The identity of a transaction is the hash
 * of a WireTransaction, therefore if you are storing data keyed by WT hash be aware that multiple different STs may
 * map to the same key (and they could be different in important ways, like validity!). The signatures on a
 * SignedTransaction might be invalid or missing: the type does not imply validity.
 */
data class SignedTransaction(val txBits: SerializedBytes<WireTransaction>,
                             val sigs: List<DigitalSignature.WithKey>) : NamedByHash {
    init {
        check(sigs.isNotEmpty())
    }

    // TODO: This needs to be reworked to ensure that the inner WireTransaction is only ever deserialised sandboxed.

    /** Lazily calculated access to the deserialised/hashed transaction data. */
    val tx: WireTransaction by lazy { WireTransaction.deserialize(txBits) }

    /** A transaction ID is the hash of the [WireTransaction]. Thus adding or removing a signature does not change it. */
    override val id: SecureHash get() = txBits.hash

    /**
     * Verify the signatures, deserialise the wire transaction and then check that the set of signatures found contains
     * the set of pubkeys in the signers list. If any signatures are missing, either throws an exception (by default) or
     * returns the list of keys that have missing signatures, depending on the parameter.
     *
     * @throws SignatureException if a signature is invalid, does not match or if any signature is missing.
     */
    @Throws(SignatureException::class)
    fun verifySignatures(throwIfSignaturesAreMissing: Boolean = true): Set<PublicKey> {
        // Embedded WireTransaction is not deserialised until after we check the signatures.
        for (sig in sigs)
            sig.verifyWithECDSA(txBits.bits)

        // Now examine the contents and ensure the sigs we have line up with the advertised list of signers.
        val missing = getMissingSignatures()
        if (missing.isNotEmpty() && throwIfSignaturesAreMissing) {
            val missingElements = getMissingKeyDescriptions(missing)
            throw SignatureException("Missing signatures for ${missingElements} on transaction ${id.prefixChars()} for ${missing.toStringsShort()}")
        }

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
            if (command.signers.any { signer -> missing.contains(signer) })
                missingElements.add(command.toString())
        }
        this.tx.notary?.owningKey.apply {
            if (missing.contains(this))
                missingElements.add("notary")
        }
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
     * Returns the set of missing signatures - a signature must be present for each signer public key.
     */
    private fun getMissingSignatures(): Set<PublicKey> {
        val requiredKeys = tx.mustSign.toSet()
        val sigKeys = sigs.map { it.by }.toSet()
        if (sigKeys.containsAll(requiredKeys)) return emptySet()
        return requiredKeys - sigKeys
    }

    /**
     * Calls [verifySignatures] to check all required signatures are present, and then calls
     * [WireTransaction.toLedgerTransaction] with the passed in [ServiceHub] to resolve the dependencies,
     * returning an unverified LedgerTransaction.
     *
     * @throws FileNotFoundException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     */
    @Throws(FileNotFoundException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(services: ServiceHub): LedgerTransaction {
        verifySignatures()
        return tx.toLedgerTransaction(services)
    }
}
