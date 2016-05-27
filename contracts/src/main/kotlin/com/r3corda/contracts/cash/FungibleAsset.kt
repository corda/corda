package com.r3corda.contracts.cash

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.utilities.Emoji
import java.security.PublicKey
import java.security.SecureRandom
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Cash-like
//

class InsufficientBalanceException(val amountMissing: Amount<Currency>) : Exception()

/**
 * Superclass for contracts representing assets which are fungible, countable and issued by a specific party. States
 * contain assets which are equivalent (such as cash of the same currency), so records of their existence can
 * be merged or split as needed where the issuer is the same. For instance, dollars issued by the Fed are fungible and
 * countable (in cents), barrels of West Texas crude are fungible and countable (oil from two small containers
 * can be poured into one large container), shares of the same class in a specific company are fungible and
 * countable, and so on.
 *
 * See [Cash] for an example subclass that implements currency.
 *
 * @param T a type that represents the asset in question. This should describe the basic type of the asset
 * (GBP, USD, oil, shares in company <X>, etc.) and any additional metadata (issuer, grade, class, etc.)
 */
abstract class FungibleAsset<T> : Contract {
    /** A state representing a cash claim against some party */
    interface State<T> : FungibleAssetState<T> {
        /** Where the underlying currency backing this ledger entry can be found (propagated) */
        override val deposit: PartyAndReference
        override val amount: Amount<Issued<T>>
        /** There must be a MoveCommand signed by this key to claim the amount */
        override val owner: PublicKey
    }

    // Just for grouping
    interface Commands : CommandData {
        interface Move : MoveCommand, Commands

        /**
         * Allows new asset states to be issued into existence: the nonce ("number used once") ensures the transaction
         * has a unique ID even when there are no inputs.
         */
        interface Issue : IssueCommand, Commands

        /**
         * A command stating that money has been withdrawn from the shared ledger and is now accounted for
         * in some other way.
         */
        interface Exit<T> : Commands { val amount: Amount<Issued<T>> }
    }

    /** This is the function EVERYONE runs */
    override fun verify(tx: TransactionForContract) {
        // Each group is a set of input/output states with distinct issuance definitions. These assets are not fungible
        // and must be kept separated for bookkeeping purposes.
        val groups = tx.groupStates() { it: FungibleAsset.State<T> -> it.issuanceDef }

        for ((inputs, outputs, token) in groups) {
            // Either inputs or outputs could be empty.
            val deposit = token.issuer
            val issuer = deposit.party

            requireThat {
                "there are no zero sized outputs" by outputs.none { it.amount.quantity == 0L }
            }

            val issueCommand = tx.commands.select<Commands.Issue>().firstOrNull()
            if (issueCommand != null) {
                verifyIssueCommand(inputs, outputs, tx, issueCommand, token, issuer)
            } else {
                val inputAmount = inputs.sumFungibleOrNull<T>() ?: throw IllegalArgumentException("there is at least one asset input for this group")
                val outputAmount = outputs.sumFungibleOrZero(token)

                // If we want to remove assets from the ledger, that must be signed for by the issuer.
                // A mis-signed or duplicated exit command will just be ignored here and result in the exit amount being zero.
                val exitCommand = tx.commands.select<Commands.Exit<T>>(party = issuer).singleOrNull()
                val amountExitingLedger = exitCommand?.value?.amount ?: Amount(0, token)

                requireThat {
                    "there are no zero sized inputs" by inputs.none { it.amount.quantity == 0L }
                    "for deposit ${deposit.reference} at issuer ${deposit.party.name} the amounts balance" by
                            (inputAmount == outputAmount + amountExitingLedger)
                }

                verifyMoveCommand<Commands.Move>(inputs, tx)
            }
        }
    }

    private fun verifyIssueCommand(inputs: List<State<T>>,
                                   outputs: List<State<T>>,
                                   tx: TransactionForContract,
                                   issueCommand: AuthenticatedObject<Commands.Issue>,
                                   token: Issued<T>,
                                   issuer: Party) {
        // If we have an issue command, perform special processing: the group is allowed to have no inputs,
        // and the output states must have a deposit reference owned by the signer.
        //
        // Whilst the transaction *may* have no inputs, it can have them, and in this case the outputs must
        // sum to more than the inputs. An issuance of zero size is not allowed.
        //
        // Note that this means literally anyone with access to the network can issue asset claims of arbitrary
        // amounts! It is up to the recipient to decide if the backing party is trustworthy or not, via some
        // external mechanism (such as locally defined rules on which parties are trustworthy).

        // The grouping ensures that all outputs have the same deposit reference and token.
        val inputAmount = inputs.sumFungibleOrZero(token)
        val outputAmount = outputs.sumFungible<T>()
        val assetCommands = tx.commands.select<FungibleAsset.Commands>()
        requireThat {
            "the issue command has a nonce" by (issueCommand.value.nonce != 0L)
            "output deposits are owned by a command signer" by (issuer in issueCommand.signingParties)
            "output values sum to more than the inputs" by (outputAmount > inputAmount)
            "there is only a single issue command" by (assetCommands.count() == 1)
        }
    }
}


// Small DSL extensions.

/**
 * Sums the asset states in the list belonging to a single owner, throwing an exception
 * if there are none, or if any of the asset states cannot be added together (i.e. are
 * different tokens).
 */
fun <T> Iterable<ContractState>.sumFungibleBy(owner: PublicKey) = filterIsInstance<FungibleAsset.State<T>>().filter { it.owner == owner }.map { it.amount }.sumOrThrow()

/**
 * Sums the asset states in the list, throwing an exception if there are none, or if any of the asset
 * states cannot be added together (i.e. are different tokens).
 */
fun <T> Iterable<ContractState>.sumFungible() = filterIsInstance<FungibleAsset.State<T>>().map { it.amount }.sumOrThrow()

/** Sums the asset states in the list, returning null if there are none. */
fun <T> Iterable<ContractState>.sumFungibleOrNull() = filterIsInstance<FungibleAsset.State<T>>().map { it.amount }.sumOrNull()

/** Sums the asset states in the list, returning zero of the given token if there are none. */
fun <T> Iterable<ContractState>.sumFungibleOrZero(token: Issued<T>) = filterIsInstance<FungibleAsset.State<T>>().map { it.amount }.sumOrZero(token)