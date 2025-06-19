package net.corda.contracts.incompatible.version2

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.OpaqueBytes

class AttachmentContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val state = tx.outputsOfType<State>().single()
        // we check that at least one has the matching hash, the other will be the contract
        require(tx.attachments.any { it.id == SecureHash.SHA256(state.opaqueBytes.bytes) }) {"At least one attachment in transaction must match hash ${state.opaqueBytes}"}
    }

    object Command : TypeOnlyCommandData()

    data class State(val opaqueBytes: OpaqueBytes) : ContractState {
        override val participants: List<AbstractParty> = emptyList()
    }
}

const val ATTACHMENT_PROGRAM_ID = "net.corda.contracts.incompatible.version2.AttachmentContract"
