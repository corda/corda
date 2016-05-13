package contracts.cash

import core.Amount
import core.OwnableState
import core.PartyAndReference

/**
 * Common elements of cash contract states.
 */
interface CommonCashState<I : CashIssuanceDefinition> : OwnableState {
    val issuanceDef: I
    /** Where the underlying currency backing this ledger entry can be found (propagated) */
    val deposit: PartyAndReference
    val amount: Amount
}