/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import core.crypto.SecureHash
import core.messaging.MessagingService
import core.messaging.NetworkMap
import java.io.InputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * This file defines various 'services' which are not currently fleshed out. A service is a module that provides
 * immutable snapshots of data that may be changing in response to user or network events.
 */

/**
 * A wallet (name may be temporary) wraps a set of states that are useful for us to keep track of, for instance,
 * because we own them. This class represents an immutable, stable state of a wallet: it is guaranteed not to
 * change out from underneath you, even though the canonical currently-best-known wallet may change as we learn
 * about new transactions from our peers and generate new transactions that consume states ourselves.
 */
data class Wallet(val states: List<StateAndRef<OwnableState>>) {
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : OwnableState> statesOfType() = states.filter { it.state is T } as List<StateAndRef<T>>
}

/**
 * A [WalletService] is responsible for securely and safely persisting the current state of a wallet to storage. The
 * wallet service vends immutable snapshots of the current wallet for working with: if you build a transaction based
 * on a wallet that isn't current, be aware that it may end up being invalid if the states that were used have been
 * consumed by someone else first!
 */
interface WalletService {
    /**
     * Returns a read-only snapshot of the wallet at the time the call is made. Note that if you consume states or
     * keys in this wallet, you must inform the wallet service so it can update its internal state.
     */
    val currentWallet: Wallet

    /**
     * Returns a snapshot of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null, not 0.
     */
    val cashBalances: Map<Currency, Amount>

    /**
     * Possibly update the wallet by marking as spent states that these transactions consume, and adding any relevant
     * new states that they create. You should only insert transactions that have been successfully verified here!
     *
     * Returns the new wallet that resulted from applying the transactions (note: it may quickly become out of date).
     *
     * TODO: Consider if there's a good way to enforce the must-be-verified requirement in the type system.
     */
    fun notifyAll(txns: Iterable<WireTransaction>): Wallet

    /** Same as notifyAll but with a single transaction. */
    fun notify(tx: WireTransaction): Wallet = notifyAll(listOf(tx))
}

/**
 * The KMS is responsible for storing and using private keys to sign things. An implementation of this may, for example,
 * call out to a hardware security module that enforces various auditing and frequency-of-use requirements.
 *
 * The current interface is obviously not usable for those use cases: this is just where we'd put a real signing
 * interface if/when one is developed.
 */
interface KeyManagementService {
    /** Returns a snapshot of the current pubkey->privkey mapping. */
    val keys: Map<PublicKey, PrivateKey>

    fun toPrivate(publicKey: PublicKey) = keys[publicKey] ?: throw IllegalStateException("No private key known for requested public key")

    /** Generates a new random key and adds it to the exposed map. */
    fun freshKey(): KeyPair
}

/**
 * A sketch of an interface to a simple key/value storage system. Intended for persistence of simple blobs like
 * transactions, serialised protocol state machines and so on. Again, this isn't intended to imply lack of SQL or
 * anything like that, this interface is only big enough to support the prototyping work.
 */
interface StorageService {
    /** TODO: Temp scaffolding that will go away eventually. */
    fun <K,V> getMap(tableName: String): MutableMap<K, V>

    /**
     * A map of hash->tx where tx has been signature/contract validated and the states are known to be correct.
     * The signatures aren't technically needed after that point, but we keep them around so that we can relay
     * the transaction data to other nodes that need it.
     */
    val validatedTransactions: MutableMap<SecureHash, SignedTransaction>

    /** Provides access to storage of arbitrary JAR files (which may contain only data, no code). */
    val attachments: AttachmentStorage

    /**
     * A map of program hash->contract class type, used for verification.
     */
    val contractPrograms: ContractFactory

    /**
     * Returns the legal identity that this node is configured with. Assumed to be initialised when the node is
     * first installed.
     */
    val myLegalIdentity: Party
    val myLegalIdentityKey: KeyPair
}

/**
 * An attachment store records potentially large binary objects, identified by their hash. Note that attachments are
 * immutable and can never be erased once inserted!
 */
interface AttachmentStorage {
    /**
     * Returns a newly opened stream for the given locally stored attachment, or null if no such attachment is known.
     * The returned stream must be closed when you are done with it to avoid resource leaks. You should probably wrap
     * the result in a [JarInputStream] unless you're sending it somewhere, there is a convenience helper for this
     * on [Attachment].
     */
    fun openAttachment(id: SecureHash): Attachment?

    /**
     * Inserts the given attachment into the store, does *not* close the input stream. This can be an intensive
     * operation due to the need to copy the bytes to disk and hash them along the way.
     *
     * Note that you should not pass a [JarInputStream] into this method and it will throw if you do, because access
     * to the raw byte stream is required.
     *
     * @throws FileAlreadyExistsException if the given byte stream has already been inserted.
     * @throws IllegalArgumentException if the given byte stream is empty or a [JarInputStream]
     * @throws IOException if something went wrong.
     */
    fun importAttachment(jar: InputStream): SecureHash
}

/**
 * A service hub simply vends references to the other services a node has. Some of those services may be missing or
 * mocked out. This class is useful to pass to chunks of pluggable code that might have need of many different kinds of
 * functionality and you don't want to hard-code which types in the interface.
 */
interface ServiceHub {
    val walletService: WalletService
    val keyManagementService: KeyManagementService
    val identityService: IdentityService
    val storageService: StorageService
    val networkService: MessagingService
    val networkMapService: NetworkMap

    /**
     * Given a [LedgerTransaction], looks up all its dependencies in the local database, uses the identity service to map
     * the [SignedTransaction]s the DB gives back into [LedgerTransaction]s, and then runs the smart contracts for the
     * transaction. If no exception is thrown, the transaction is valid.
     */
    fun verifyTransaction(ltx: LedgerTransaction) {
        val dependencies = ltx.inputs.map {
            storageService.validatedTransactions[it.txhash] ?: throw TransactionResolutionException(it.txhash)
        }
        val ltxns = dependencies.map { it.verifyToLedgerTransaction(identityService) }
        TransactionGroup(setOf(ltx), ltxns.toSet()).verify(storageService.contractPrograms)
    }
}
