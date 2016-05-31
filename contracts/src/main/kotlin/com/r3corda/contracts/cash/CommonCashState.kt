package com.r3corda.contracts.cash

import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.OwnableState
import com.r3corda.core.contracts.PartyAndReference
import java.util.Currency

/**
 * Common elements of cash contract states.
 */
interface CommonCashState<I : CashIssuanceDefinition> : OwnableState {
    val issuanceDef: I
    /** Where the underlying currency backing this ledger entry can be found (propagated) */
    val deposit: PartyAndReference
    val amount: Amount<Currency>
}