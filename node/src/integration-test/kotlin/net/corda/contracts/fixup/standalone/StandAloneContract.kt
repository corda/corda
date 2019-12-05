package net.corda.contracts.fixup.standalone

import net.corda.core.contracts.Contract
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.transactions.LedgerTransaction

/**
 * Add a [Contract] to this CorDapp so that the Node will
 * automatically upload this CorDapp into [AttachmentStorage].
 */
@Suppress("unused")
class StandAloneContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        throw UnsupportedOperationException("Dummy contract - not used")
    }
}