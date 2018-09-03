package net.corda.notaryhealthcheck.contract

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

/**
 * Minimal contract to use for checking that notarisation works
 */
class NullContract : Contract {
    override fun verify(tx: LedgerTransaction) {}
}
