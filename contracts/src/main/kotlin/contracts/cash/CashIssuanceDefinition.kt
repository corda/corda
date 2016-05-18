package contracts.cash

import core.contracts.IssuanceDefinition
import core.contracts.PartyAndReference
import java.util.*

/**
 * Subset of cash-like contract state, containing the issuance definition. If these definitions match for two
 * contracts' states, those states can be aggregated.
 */
interface CashIssuanceDefinition : IssuanceDefinition {
    /** Where the underlying currency backing this ledger entry can be found (propagated) */
    val deposit: PartyAndReference
    val currency: Currency
}