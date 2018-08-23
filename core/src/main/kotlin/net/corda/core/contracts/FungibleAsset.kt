/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import java.security.PublicKey

/**
 * Thrown if a request is made to spend an amount of a [FungibleAsset] but there aren't enough tokens in the vault.
 *
 * @property amountMissing An [Amount] that specifies how many tokens were missing.
 */
class InsufficientBalanceException(val amountMissing: Amount<*>) : FlowException("Insufficient balance, missing $amountMissing")

/**
 * Interface for contract states representing assets which are fungible, countable and issued by a
 * specific party. States contain assets which are equivalent (such as cash of the same currency),
 * so records of their existence can be merged or split as needed where the issuer is the same. For
 * instance, dollars issued by the Fed are fungible and countable (in cents), barrels of West Texas
 * crude are fungible and countable (oil from two small containers can be poured into one large
 * container), shares of the same class in a specific company are fungible and countable, and so on.
 *
 * An example usage would be a cash transaction contract that implements currency using state objects that implement
 * this interface.
 *
 * @param T a type that represents the asset in question. This should describe the basic type of the asset
 * (GBP, USD, oil, shares in company <X>, etc.) and any additional metadata (issuer, grade, class, etc.).
 */
@KeepForDJVM
interface FungibleAsset<T : Any> : OwnableState {
    /**
     * Amount represents a positive quantity of some issued product which can be cash, tokens, assets, or generally
     * anything else that's quantifiable with integer quantities. See [Issued] and [Amount] for more details.
     */
    val amount: Amount<Issued<T>>

    /**
     * There must be an ExitCommand signed by these keys to destroy the amount. While all states require their
     * owner to sign, some (i.e. cash) also require the issuer.
     */
    val exitKeys: Collection<PublicKey>

    /**
     * Copies the underlying data structure, replacing the amount and owner fields with the new values and leaving the
     * rest (exitKeys) alone.
     */
    fun withNewOwnerAndAmount(newAmount: Amount<Issued<T>>, newOwner: AbstractParty): FungibleAsset<T>
}
