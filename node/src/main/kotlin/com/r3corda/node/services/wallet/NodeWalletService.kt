package com.r3corda.node.services.wallet

import com.r3corda.core.ThreadBox
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.Wallet
import com.r3corda.core.node.services.WalletService
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import com.r3corda.node.services.api.ServiceHubInternal
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * This class implements a simple, in memory wallet that tracks states that are owned by us, and also has a convenience
 * method to auto-generate some self-issued cash states that can be used for test trading. A real wallet would persist
 * states relevant to us into a database and once such a wallet is implemented, this scaffolding can be removed.
 */
@ThreadSafe
class NodeWalletService(private val services: ServiceHubInternal) : SingletonSerializeAsToken(), WalletService {
    private val log = loggerFor<NodeWalletService>()

    // Variables inside InnerState are protected with a lock by the ThreadBox and aren't in scope unless you're
    // inside mutex.locked {} code block. So we can't forget to take the lock unless we accidentally leak a reference
    // to wallet somewhere.
    private class InnerState {
        var wallet: Wallet = WalletImpl(emptyList<StateAndRef<OwnableState>>())
    }

    private val mutex = ThreadBox(InnerState())

    override val currentWallet: Wallet get() = mutex.locked { wallet }

    private val _updatesPublisher = PublishSubject.create<Wallet.Update>()

    override val updates: Observable<Wallet.Update>
        get() = _updatesPublisher

    /**
     * Returns a snapshot of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null, not 0.
     */
    override val cashBalances: Map<Currency, Amount<Currency>> get() = mutex.locked { wallet }.cashBalances

    /**
     * Returns a snapshot of the heads of LinearStates
     */
    override val linearHeads: Map<SecureHash, StateAndRef<LinearState>>
        get() = mutex.locked { wallet }.let { wallet ->
            wallet.states.filterStatesOfType<LinearState>().associateBy { it.state.data.thread }.mapValues { it.value }
        }

    override fun notifyAll(txns: Iterable<WireTransaction>): Wallet {
        val ourKeys = services.keyManagementService.keys.keys

        // Note how terribly incomplete this all is!
        //
        // - We don't notify anyone of anything, there are no event listeners.
        // - We don't handle or even notice invalidations due to double spends of things in our wallet.
        // - We have no concept of confidence (for txns where there is no definite finality).
        // - No notification that keys are used, for the case where we observe a spend of our own states.
        // - No ability to create complex spends.
        // - No logging or tracking of how the wallet got into this state.
        // - No persistence.
        // - Does tx relevancy calculation and key management need to be interlocked? Probably yes.
        //
        // ... and many other things .... (Wallet.java in bitcoinj is several thousand lines long)

        var netDelta = Wallet.NoUpdate
        val changedWallet = mutex.locked {
            // Starting from the current wallet, keep applying the transaction updates, calculating a new Wallet each
            // time, until we get to the result (this is perhaps a bit inefficient, but it's functional and easily
            // unit tested).
            val walletAndNetDelta = txns.fold(Pair(currentWallet, Wallet.NoUpdate)) { walletAndDelta, tx ->
                val (wallet, delta) = walletAndDelta.first.update(tx, ourKeys)
                val combinedDelta = delta + walletAndDelta.second
                Pair(wallet, combinedDelta)
            }
            wallet = walletAndNetDelta.first
            netDelta = walletAndNetDelta.second
            return@locked wallet
        }
        if (netDelta != Wallet.NoUpdate) {
            _updatesPublisher.onNext(netDelta)
        }
        return changedWallet
    }

    private fun isRelevant(state: ContractState, ourKeys: Set<PublicKey>): Boolean {
        return if (state is OwnableState) {
            state.owner in ourKeys
        } else if (state is LinearState) {
            // It's potentially of interest to the wallet
            state.isRelevant(ourKeys)
        } else {
            false
        }
    }

    private fun Wallet.update(tx: WireTransaction, ourKeys: Set<PublicKey>): Pair<Wallet, Wallet.Update> {
        val ourNewStates = tx.outputs.
                filter { isRelevant(it.data, ourKeys) }.
                map { tx.outRef<ContractState>(it.data) }

        // Now calculate the states that are being spent by this transaction.
        val consumed: Set<StateRef> = states.map { it.ref }.intersect(tx.inputs)

        // Is transaction irrelevant?
        if (consumed.isEmpty() && ourNewStates.isEmpty()) {
            log.trace { "tx ${tx.id} was irrelevant to this wallet, ignoring" }
            return Pair(this, Wallet.NoUpdate)
        }

        val change = Wallet.Update(consumed, HashSet(ourNewStates))

        // And calculate the new wallet.
        val newStates = states.filter { it.ref !in consumed } + ourNewStates

        log.trace {
            "Applied tx ${tx.id.prefixChars()} to the wallet: consumed ${consumed.size} states and added ${newStates.size}"
        }

        return Pair(WalletImpl(newStates), change)
    }
}
