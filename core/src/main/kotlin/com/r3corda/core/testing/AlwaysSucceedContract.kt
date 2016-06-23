package com.r3corda.core.testing

import com.r3corda.core.contracts.Contract
import com.r3corda.core.contracts.TransactionForContract
import com.r3corda.core.crypto.SecureHash

class AlwaysSucceedContract(override val legalContractReference: SecureHash = SecureHash.sha256("Always succeed contract")) : Contract {
    override fun verify(tx: TransactionForContract) {
    }
}
