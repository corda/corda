package com.r3corda.core.testing

import com.r3corda.core.ThreadBox
import com.r3corda.core.bufferUntilSubscribed
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.Vault
import com.r3corda.core.node.services.VaultService
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.core.transactions.TransactionBuilder
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.loggerFor
import com.r3corda.core.utilities.trace
import rx.Observable
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * This class implements a simple, in memory vault that tracks states that are owned by us, and also has a convenience
 * method to auto-generate some self-issued cash states that can be used for test trading. A real vault would persist
 * states relevant to us into a database and once such a vault is implemented, this scaffolding can be removed.
 */
@ThreadSafe
open class InMemoryVaultService(protected val services: ServiceHub) : SingletonSerializeAsToken(), VaultService {
    open protected val log = loggerFor<InMemoryVaultService>()

    // Variables inside InnerState are protected with a lock by the ThreadBox and aren't in scope unless you're
    // inside mutex.locked {} code block. So we can't forget to take the lock unless we accidentally leak a reference
    // to vault somewhere.
    protected class InnerState {
        var vault = Vault(emptyList<StateAndRef<ContractState>>())
        val _updatesPublisher = PublishSubject.create<Vault.Update>()
    }

    protected val mutex = ThreadBox(InnerState())

    override val currentVault: Vault get() = mutex.locked { vault }

    override val updates: Observable<Vault.Update>
        get() = mutex.content._updatesPublisher

    @Suppress("UNCHECKED_CAST")
    override val cashBalances: Map<Currency, Amount<Currency>>
        get() = currentVault.states.
                // Select the states we own which are cash, ignore the rest, take the amounts.
                mapNotNull { (it.state.data as? FungibleAsset<Currency>)?.amount }.
                // Turn into a Map<Currency, List<Amount>> like { GBP -> (£100, £500, etc), USD -> ($2000, $50) }
                groupBy { it.token.product }.
                // Collapse to Map<Currency, Amount> by summing all the amounts of the same currency together.
                mapValues { it.value.map { Amount(it.quantity, it.token.product) }.sumOrThrow() }

    override fun track(): Pair<Vault, Observable<Vault.Update>> {
        return mutex.locked {
            Pair(vault, updates.bufferUntilSubscribed())
        }
    }

    /**
     * Returns a snapshot of the heads of LinearStates.
     */
    override val linearHeads: Map<UniqueIdentifier, StateAndRef<LinearState>>
        get() = currentVault.let { vault ->
            vault.states.filterStatesOfType<LinearState>().associateBy { it.state.data.linearId }.mapValues { it.value }
        }

    override fun notifyAll(txns: Iterable<WireTransaction>): Vault {
        val ourKeys = services.keyManagementService.keys.keys

        // Note how terribly incomplete this all is!
        //
        // - We don't notify anyone of anything, there are no event listeners.
        // - We don't handle or even notice invalidations due to double spends of things in our vault.
        // - We have no concept of confidence (for txns where there is no definite finality).
        // - No notification that keys are used, for the case where we observe a spend of our own states.
        // - No ability to create complex spends.
        // - No logging or tracking of how the vault got into this state.
        // - No persistence.
        // - Does tx relevancy calculation and key management need to be interlocked? Probably yes.
        //
        // ... and many other things .... (Wallet.java in bitcoinj is several thousand lines long)

        var netDelta = Vault.NoUpdate
        val changedVault = mutex.locked {
            // Starting from the current vault, keep applying the transaction updates, calculating a new vault each
            // time, until we get to the result (this is perhaps a bit inefficient, but it's functional and easily
            // unit tested).
            val vaultAndNetDelta = txns.fold(Pair(currentVault, Vault.NoUpdate)) { vaultAndDelta, tx ->
                val (vault, delta) = vaultAndDelta.first.update(tx, ourKeys)
                val combinedDelta = delta + vaultAndDelta.second
                Pair(vault, combinedDelta)
            }

            vault = vaultAndNetDelta.first
            netDelta = vaultAndNetDelta.second
            return@locked vault
        }

        if (netDelta != Vault.NoUpdate) {
            mutex.locked {
                _updatesPublisher.onNext(netDelta)
            }
        }
        return changedVault
    }

    override fun generateSpend(tx: TransactionBuilder, amount: Amount<Currency>, to: PublicKey, onlyFromParties: Set<Party>?): Pair<TransactionBuilder, List<PublicKey>> {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    private fun isRelevant(state: ContractState, ourKeys: Set<PublicKey>): Boolean {
        return if (state is OwnableState) {
            state.owner in ourKeys
        } else if (state is LinearState) {
            // It's potentially of interest to the vault
            state.isRelevant(ourKeys)
        } else {
            false
        }
    }

    private fun Vault.update(tx: WireTransaction, ourKeys: Set<PublicKey>): Pair<Vault, Vault.Update> {
        val ourNewStates = tx.outputs.
                filter { isRelevant(it.data, ourKeys) }.
                map { tx.outRef<ContractState>(it.data) }

        // Now calculate the states that are being spent by this transaction.
        val consumed: Set<StateRef> = states.map { it.ref }.intersect(tx.inputs)

        // Is transaction irrelevant?
        if (consumed.isEmpty() && ourNewStates.isEmpty()) {
            log.trace { "tx ${tx.id} was irrelevant to this vault, ignoring" }
            return Pair(this, Vault.NoUpdate)
        }

        val change = Vault.Update(consumed, HashSet(ourNewStates))

        // And calculate the new vault.
        val newStates = states.filter { it.ref !in consumed } + ourNewStates

        log.trace {
            "Applied tx ${tx.id.prefixChars()} to the vault: consumed ${consumed.size} states and added ${newStates.size}"
        }

        return Pair(Vault(newStates), change)
    }

}
