package net.corda.finance.contracts.asset

import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Issued
import java.util.*

/**
 * A command stating that money has been withdrawn from the shared ledger and is now accounted for
 * in some other way.
 */
data class Exit(val amount: Amount<Issued<Currency>>) : CommandData