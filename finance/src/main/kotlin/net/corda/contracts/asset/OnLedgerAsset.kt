package net.corda.contracts.asset

import net.corda.contracts.clause.AbstractConserveAmount
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.transactions.TransactionBuilder

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Generic contract for assets on a ledger
//

/**
 * An asset transaction may split and merge assets represented by a set of (issuer, depositRef) pairs, across multiple
 * input and output states. Imagine a Bitcoin transaction but in which all UTXOs had a colour (a blend of
 * issuer+depositRef) and you couldn't merge outputs of two colours together, but you COULD put them in the same
 * transaction.
 *
 * The goal of this design is to ensure that assets can be withdrawn from the ledger easily: if you receive some asset
 * via this contract, you always know where to go in order to extract it from the R3 ledger, no matter how many hands
 * it has passed through in the intervening time.
 *
 * At the same time, other contracts that just want assets and don't care much who is currently holding it can ignore
 * the issuer/depositRefs and just examine the amount fields.
 */
abstract class OnLedgerAsset<T : Any, C : CommandData, S : FungibleAsset<T>> : Contract {
    abstract fun extractCommands(commands: Collection<AuthenticatedObject<CommandData>>): Collection<AuthenticatedObject<C>>
    abstract val conserveClause: AbstractConserveAmount<S, C, T>

    /**
     * Generate an transaction exiting assets from the ledger.
     *
     * @param tx transaction builder to add states and commands to.
     * @param amountIssued the amount to be exited, represented as a quantity of issued currency.
     * @param changeKey the key to send any change to. This needs to be explicitly stated as the input states are not
     * necessarily owned by us.
     * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
     * the responsibility of the caller to check that they do not exit funds held by others.
     * @return the public key of the assets issuer, who must sign the transaction for it to be valid.
     */
    @Throws(InsufficientBalanceException::class)
    fun generateExit(tx: TransactionBuilder, amountIssued: Amount<Issued<T>>,
                     assetStates: List<StateAndRef<S>>): CompositeKey {
        return conserveClause.generateExit(
                tx,
                amountIssued,
                assetStates,
                deriveState = { state, amount, owner -> deriveState(state, amount, owner) },
                generateMoveCommand = { -> generateMoveCommand() },
                generateExitCommand = { amount -> generateExitCommand(amount) }
        )
    }

    abstract fun generateExitCommand(amount: Amount<Issued<T>>): FungibleAsset.Commands.Exit<T>
    abstract fun generateIssueCommand(): FungibleAsset.Commands.Issue
    abstract fun generateMoveCommand(): FungibleAsset.Commands.Move

    /**
     * Derive a new transaction state based on the given example, with amount and owner modified. This allows concrete
     * implementations to have fields in their state which we don't know about here, and we simply leave them untouched
     * when sending out "change" from spending/exiting.
     */
    abstract fun deriveState(txState: TransactionState<S>, amount: Amount<Issued<T>>, owner: CompositeKey): TransactionState<S>
}
