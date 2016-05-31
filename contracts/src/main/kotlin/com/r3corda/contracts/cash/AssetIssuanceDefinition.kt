package com.r3corda.contracts.cash

import com.r3corda.core.contracts.IssuanceDefinition
import com.r3corda.core.contracts.PartyAndReference
import java.util.*

/**
 * Subset of cash-like contract state, containing the issuance definition. If these definitions match for two
 * contracts' states, those states can be aggregated.
 */
interface AssetIssuanceDefinition<T> : IssuanceDefinition {
    /** Where the underlying asset backing this ledger entry can be found (propagated) */
    val deposit: PartyAndReference
    val token: T
}