package net.corda.testing

import net.corda.core.contracts.Contract
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.LedgerTransaction

class AlwaysSucceedContract(override val legalContractReference: SecureHash = SecureHash.sha256("Always succeed contract")) : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}
