package contracts.cash

import core.contracts.Amount
import core.contracts.OwnableState
import core.contracts.PartyAndReference

/**
 * Common elements of cash contract states.
 */
interface CommonCashState<I : CashIssuanceDefinition> : OwnableState {
    val issuanceDef: I
    /** Where the underlying currency backing this ledger entry can be found (propagated) */
    val deposit: PartyAndReference
    val amount: Amount
}