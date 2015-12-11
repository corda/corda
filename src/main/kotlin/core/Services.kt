/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant

/**
 * This file defines various 'services' which are not currently fleshed out. A service is a module that provides
 * immutable snapshots of data that may be changing in response to user or network events.
 */

/**
 * A wallet (name may be temporary) wraps a set of private keys, and a set of states that are known about and that can
 * be influenced by those keys, for instance, because we own them. This class represents an immutable, stable state
 * of a wallet: it is guaranteed not to change out from underneath you, even though the canonical currently-best-known
 * wallet may change as we learn new transactions from our peers.
 */
data class Wallet(val states: List<StateAndRef<OwnableState>>, val keys: Map<PublicKey, PrivateKey>)

/**
 * A [WalletService] is responsible for securely and safely persisting the current state of a wallet to storage. The
 * wallet service vends immutable snapshots of the current wallet for working with: if you build a transaction based
 * on a wallet that isn't current, be aware that it may end up being invalid if the states that were used have been
 * consumed by someone else first!
 */
interface WalletService {
    val currentWallet: Wallet
}

/**
 * An identity service maintains an bidirectional map of [Party]s to their associated public keys and thus supports
 * lookup of a party given its key. This is obviously very incomplete and does not reflect everything a real identity
 * service would provide.
 */
interface IdentityService {
    fun partyFromKey(key: PublicKey): Party
}

/**
 * Simple interface (for testing) to an abstract timestamping service, in the style of RFC 3161. Note that this is not
 * 'timestamping' in the block chain sense, but rather, implies a semi-trusted third party taking a reading of the
 * current time, typically from an atomic clock, and then digitally signing (current time, hash) to produce a timestamp
 * triple (signature, time, hash). The purpose of these timestamps is to locate a transaction in the timeline, which is
 * important in the absence of blocks. Here we model the timestamp as an opaque byte array.
 */
interface TimestamperService {
    fun timestamp(hash: SecureHash): ByteArray
    fun verifyTimestamp(hash: SecureHash, signedTimestamp: ByteArray): Instant
}

/**
 * A service hub simply vends references to the other services a node has. Some of those services may be missing or
 * mocked out. This class is useful to pass to chunks of pluggable code that might have need of many different kinds of
 * functionality and you don't want to hard-code which types in the interface.
 */
interface ServiceHub {
    val walletService: WalletService
    val identityService: IdentityService
    val timestampingService: TimestamperService
}