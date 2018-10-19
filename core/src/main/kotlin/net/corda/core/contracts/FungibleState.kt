package net.corda.core.contracts

import net.corda.core.KeepForDJVM

/**
 * Interface to represent things which are fungible, this means that there is an expectation that these things can
 * be split and merged. That's the only assumption made by this interface.
 *
 * This interface has been defined in addition to [FungibleAsset] to provide some additional flexibility which
 * [FungibleAsset] lacks, in particular:
 *
 * - [FungibleAsset] defines an amount property of type Amount<Issued<T>>, therefore there is an assumption that all
 *   fungible things are issued by a single well known party but this is not always the case. For example,
 *   crypto-currencies like Bitcoin are generated periodically by a pool of pseudo-anonymous miners
 *   and Corda can support such crypto-currencies.
 * - [FungibleAsset] implements [OwnableState], as such there is an assumption that all fungible things are ownable.
 *   This is not always true as fungible derivative contracts exist, for example.
 *
 * The expectation is that this interface should be combined with the other core state interfaces such as
 * [OwnableState] and others created at the application layer.
 *
 * @param T a type that represents the fungible thing in question. This should describe the basic type of the asset
 * (GBP, USD, oil, shares in company <X>, etc.) and any additional metadata (issuer, grade, class, etc.). An
 * upper-bound is not specified for [T] to ensure flexibility. Typically, a class would be provided that implements
 * [TokenizableAssetInfo].
 */
// DOCSTART 1
@KeepForDJVM
interface FungibleState<T : Any> : ContractState {
    /**
     * Amount represents a positive quantity of some token which can be cash, tokens, stock, agreements, or generally
     * anything else that's quantifiable with integer quantities. See [Amount] for more details.
     */
    val amount: Amount<T>
}
// DOCEND 1

