package com.r3corda.contracts.cash

import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.OwnableState
import com.r3corda.core.contracts.PartyAndReference
import com.r3corda.core.contracts.Issued

/**
 * Common elements of cash contract states.
 */
interface FungibleAssetState<T> : OwnableState {
    val issuanceDef: Issued<T>
    /** Where the underlying currency backing this ledger entry can be found (propagated) */
    val deposit: PartyAndReference
    val amount: Amount<Issued<T>>
}