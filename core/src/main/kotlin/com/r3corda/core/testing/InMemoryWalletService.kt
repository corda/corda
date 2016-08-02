package com.r3corda.core.testing

import com.r3corda.core.ThreadBox
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.Wallet
import com.r3corda.core.node.services.WalletService
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
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
open class InMemoryWalletService(protected val services: ServiceHub) : SingletonSerializeAsToken(), WalletService {
    class ClashingThreads(threads: Set<SecureHash>, transactions: Iterable<WireTransaction>) :
            Exception("There are multiple linear head states after processing transactions $transactions. The clashing thread(s): $threads")

    open protected val log = loggerFor<InMemoryWalletService>()

    // Variables inside InnerState are protected with a lock by the ThreadBox and aren't in scope unless you're
    // inside mutex.locked {} code block. So we can't forget to take the lock unless we accidentally leak a reference
    // to wallet somewhere.
    protected class InnerState {
        var wallet = Wallet(emptyList<StateAndRef<ContractState>>())
    }

    protected val mutex = ThreadBox(InnerState())

    override val currentWallet: Wallet get() = mutex.locked { wallet }

    private val _updatesPublisher = PublishSubject.create<Wallet.Update>()

    override val updates: Observable<Wallet.Update>
        get() = _updatesPublisher

    /**
     * Returns a snapshot of the heads of LinearStates.
     */
    override val linearHeads: Map<SecureHash, StateAndRef<LinearState>>
        get() = currentWallet.let { wallet ->
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

            // TODO: we need to remove the clashing threads concepts and support potential duplicate threads
            //       because two different nodes can have two different sets of threads and so currently it's possible
            //       for only one party to have a clash which interferes with determinism of the transactions.
            val clashingThreads = walletAndNetDelta.first.clashingThreads
            if (!clashingThreads.isEmpty()) {
                throw ClashingThreads(clashingThreads, txns)
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

        return Pair(Wallet(newStates), change)
    }

    companion object {

        // Returns the set of LinearState threads that clash in the wallet
        val Wallet.clashingThreads: Set<SecureHash> get() {
            val clashingThreads = HashSet<SecureHash>()
            val threadsSeen = HashSet<SecureHash>()
            for (linearState in states.filterStatesOfType<LinearState>()) {
                val thread = linearState.state.data.thread
                if (threadsSeen.contains(thread)) {
                    clashingThreads.add(thread)
                } else {
                    threadsSeen.add(thread)
                }
            }
            return clashingThreads
        }

    }
}
