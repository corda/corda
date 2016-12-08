package net.corda.testing

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TransactionForContract
import net.corda.core.crypto.SecureHash

class AlwaysSucceedContract(override val legalContractReference: SecureHash = SecureHash.sha256("Always succeed contract")) : Contract {
    override fun verify(tx: TransactionForContract) {
    }
}
