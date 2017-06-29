package net.corda.core.node

import net.corda.core.contracts.*
import net.corda.core.crypto.DigitalSignature
import net.corda.core.node.services.*
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey
import java.time.Clock

/**
 * Subset of node services that are used for loading transactions from the wire into fully resolved, looked up
 * forms ready for verification.
 *
 * @see ServiceHub
 */
interface ServicesForResolution {
    val identityService: IdentityService
    /** Provides access to storage of arbitrary JAR files (which may contain only data, no code). */
    val attachments: AttachmentStorage
    /**
     * Given a [StateRef] loads the referenced transaction and looks up the specified output [ContractState].
     *
     * @throws TransactionResolutionException if the [StateRef] points to a non-existent transaction.
     */
    @Throws(TransactionResolutionException::class)
    fun loadState(stateRef: StateRef): TransactionState<*>
}

/**
 * A service hub simply vends references to the other services a node has. Some of those services may be missing or
 * mocked out. This class is useful to pass to chunks of pluggable code that might have need of many different kinds of
 * functionality and you don't want to hard-code which types in the interface.
 *
 * Any services exposed to flows (public view) need to implement [SerializeAsToken] or similar to avoid their internal
 * state from being serialized in checkpoints.
 */
interface ServiceHub : ServicesForResolution {
    val vaultService: VaultService
    val vaultQueryService: VaultQueryService
    val keyManagementService: KeyManagementService
    /**
     * A map of hash->tx where tx has been signature/contract validated and the states are known to be correct.
     * The signatures aren't technically needed after that point, but we keep them around so that we can relay
     * the transaction data to other nodes that need it.
     */
    val validatedTransactions: ReadOnlyTransactionStorage

    val networkMapCache: NetworkMapCache
    val transactionVerifierService: TransactionVerifierService
    val clock: Clock
    val myInfo: NodeInfo

    /**
     * Return the singleton instance of the given Corda service type. This is a class that is annotated with
     * [CordaService] and will have automatically been registered by the node.
     * @throws IllegalArgumentException If [type] is not annotated with [CordaService] or if the instance is not found.
     */
    fun <T : SerializeAsToken> cordaService(type: Class<T>): T

    /**
     * Given a [SignedTransaction], writes it to the local storage for validated transactions and then
     * sends them to the vault for further processing. Expects to be run within a database transaction.
     *
     * @param txs The transactions to record.
     */
    // TODO: Make this take a single tx.
    fun recordTransactions(txs: Iterable<SignedTransaction>)

    /**
     * Given some [SignedTransaction]s, writes them to the local storage for validated transactions and then
     * sends them to the vault for further processing.
     *
     * @param txs The transactions to record.
     */
    fun recordTransactions(vararg txs: SignedTransaction) = recordTransactions(txs.toList())

    /**
     * Given a [StateRef] loads the referenced transaction and looks up the specified output [ContractState].
     *
     * @throws TransactionResolutionException if the [StateRef] points to a non-existent transaction.
     */
    @Throws(TransactionResolutionException::class)
    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val definingTx = validatedTransactions.getTransaction(stateRef.txhash) ?: throw TransactionResolutionException(stateRef.txhash)
        return definingTx.tx.outputs[stateRef.index]
    }

    /**
     * Will check [logicType] and [args] against a whitelist and if acceptable then construct and initiate the protocol.
     *
     * @throws IllegalProtocolLogicException or IllegalArgumentException if there are problems with the [logicType] or [args].
     */
    fun <T : ContractState> toStateAndRef(ref: StateRef): StateAndRef<T> {
        val definingTx = validatedTransactions.getTransaction(ref.txhash) ?: throw TransactionResolutionException(ref.txhash)
        return definingTx.tx.outRef<T>(ref.index)
    }

    /**
     * Helper property to shorten code for fetching the the [PublicKey] portion of the
     * Node's primary signing identity.
     * Typical use is during signing in flows and for unit test signing.
     * When this [PublicKey] is passed into the signing methods below, or on the KeyManagementService
     * the matching [PrivateKey] will be looked up internally and used to sign.
     * If the key is actually a CompositeKey, the first leaf key hosted on this node
     * will be used to create the signature.
     */
    val legalIdentityKey: PublicKey get() = this.myInfo.legalIdentity.owningKey

    /**
     * Helper property to shorten code for fetching the the [PublicKey] portion of the
     * Node's Notary signing identity. It is required that the Node hosts a notary service,
     * otherwise an IllegalArgumentException will be thrown.
     * Typical use is during signing in flows and for unit test signing.
     * When this [PublicKey] is passed into the signing methods below, or on the KeyManagementService
     * the matching [PrivateKey] will be looked up internally and used to sign.
     * If the key is actually a [CompositeKey], the first leaf key hosted on this node
     * will be used to create the signature.
     */
    val notaryIdentityKey: PublicKey get() = this.myInfo.notaryIdentity.owningKey

    /**
     * Helper method to construct an initial partially signed transaction from a [TransactionBuilder]
     * using keys stored inside the node.
     * @param builder The [TransactionBuilder] to seal with the node's signature.
     * Any existing signatures on the builder will be preserved.
     * @param publicKey The [PublicKey] matched to the internal [PrivateKey] to use in signing this transaction.
     * If the passed in key is actually a CompositeKey the code searches for the first child key hosted within this node
     * to sign with.
     * @return Returns a SignedTransaction with the new node signature attached.
     */
    fun signInitialTransaction(builder: TransactionBuilder, publicKey: PublicKey): SignedTransaction {
        val sig = keyManagementService.sign(builder.toWireTransaction().id.bytes, publicKey)
        builder.addSignatureUnchecked(sig)
        return builder.toSignedTransaction(false)
    }


    /**
     * Helper method to construct an initial partially signed transaction from a TransactionBuilder
     * using the default identity key contained in the node.
     * @param builder The TransactionBuilder to seal with the node's signature.
     * Any existing signatures on the builder will be preserved.
     * @return Returns a SignedTransaction with the new node signature attached.
     */
    fun signInitialTransaction(builder: TransactionBuilder): SignedTransaction = signInitialTransaction(builder, legalIdentityKey)


    /**
     * Helper method to construct an initial partially signed transaction from a [TransactionBuilder]
     * using a set of keys all held in this node.
     * @param builder The [TransactionBuilder] to seal with the node's signature.
     * Any existing signatures on the builder will be preserved.
     * @param signingPubKeys A list of [PublicKeys] used to lookup the matching [PrivateKey] and sign.
     * @throws IllegalArgumentException is thrown if any keys are unavailable locally.
     * @return Returns a [SignedTransaction] with the new node signature attached.
     */
    fun signInitialTransaction(builder: TransactionBuilder, signingPubKeys: Iterable<PublicKey>): SignedTransaction {
        var stx: SignedTransaction? = null
        for (pubKey in signingPubKeys) {
            stx = if (stx == null) {
                signInitialTransaction(builder, pubKey)
            } else {
                addSignature(stx, pubKey)
            }
        }
        return stx!!
    }

    /**
     * Helper method to create an additional signature for an existing (partially) [SignedTransaction].
     * @param signedTransaction The [SignedTransaction] to which the signature will apply.
     * @param publicKey The [PublicKey] matching to a signing [PrivateKey] hosted in the node.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf key found locally will be used for signing.
     * @return The [DigitalSignature.WithKey] generated by signing with the internally held [PrivateKey].
     */
    fun createSignature(signedTransaction: SignedTransaction, publicKey: PublicKey): DigitalSignature.WithKey = keyManagementService.sign(signedTransaction.id.bytes, publicKey)

    /**
     * Helper method to create an additional signature for an existing (partially) SignedTransaction
     * using the default identity signing key of the node.
     * @param signedTransaction The SignedTransaction to which the signature will apply.
     * @return The DigitalSignature.WithKey generated by signing with the internally held identity PrivateKey.
     */
    fun createSignature(signedTransaction: SignedTransaction): DigitalSignature.WithKey = createSignature(signedTransaction, legalIdentityKey)

    /**
     * Helper method to append an additional signature to an existing (partially) [SignedTransaction].
     * @param signedTransaction The [SignedTransaction] to which the signature will be added.
     * @param publicKey The [PublicKey] matching to a signing [PrivateKey] hosted in the node.
     * If the [PublicKey] is actually a [CompositeKey] the first leaf key found locally will be used for signing.
     * @return A new [SignedTransaction] with the addition of the new signature.
     */
    fun addSignature(signedTransaction: SignedTransaction, publicKey: PublicKey): SignedTransaction = signedTransaction + createSignature(signedTransaction, publicKey)

    /**
     * Helper method to ap-pend an additional signature for an existing (partially) [SignedTransaction]
     * using the default identity signing key of the node.
     * @param signedTransaction The [SignedTransaction] to which the signature will be added.
     * @return A new [SignedTransaction] with the addition of the new signature.
     */
    fun addSignature(signedTransaction: SignedTransaction): SignedTransaction = addSignature(signedTransaction, legalIdentityKey)
}