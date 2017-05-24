package net.corda.core.contracts

import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import java.security.PublicKey

class InsufficientBalanceException(val amountMissing: Amount<*>) : FlowException("Insufficient balance, missing $amountMissing")

/**
 * Interface for contract states representing assets which are fungible, countable and issued by a
 * specific party. States contain assets which are equivalent (such as cash of the same currency),
 * so records of their existence can be merged or split as needed where the issuer is the same. For
 * instance, dollars issued by the Fed are fungible and countable (in cents), barrels of West Texas
 * crude are fungible and countable (oil from two small containers can be poured into one large
 * container), shares of the same class in a specific company are fungible and countable, and so on.
 *
 * See [Cash] for an example contract that implements currency using state objects that implement
 * this interface.
 *
 * @param T a type that represents the asset in question. This should describe the basic type of the asset
 * (GBP, USD, oil, shares in company <X>, etc.) and any additional metadata (issuer, grade, class, etc.).
 */
interface FungibleAsset<T : Any> : OwnableState {
    val amount: Amount<Issued<T>>
    /**
     * There must be an ExitCommand signed by these keys to destroy the amount. While all states require their
     * owner to sign, some (i.e. cash) also require the issuer.
     */
    val exitKeys: Collection<PublicKey>
    /** There must be a MoveCommand signed by this key to claim the amount */
    override val owner: AbstractParty

    fun move(newAmount: Amount<Issued<T>>, newOwner: AbstractParty): FungibleAsset<T>

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
        interface Exit<T : Any> : Commands {
            val amount: Amount<Issued<T>>
        }
    }
}

// Small DSL extensions.

/** Sums the asset states in the list, returning null if there are none. */
fun <T : Any> Iterable<ContractState>.sumFungibleOrNull() = filterIsInstance<FungibleAsset<T>>().map { it.amount }.sumOrNull()

/** Sums the asset states in the list, returning zero of the given token if there are none. */
fun <T : Any> Iterable<ContractState>.sumFungibleOrZero(token: Issued<T>) = filterIsInstance<FungibleAsset<T>>().map { it.amount }.sumOrZero(token)

