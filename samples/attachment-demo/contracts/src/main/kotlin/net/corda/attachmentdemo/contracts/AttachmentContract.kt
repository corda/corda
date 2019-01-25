package net.corda.attachmentdemo.contracts

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

class AttachmentContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val state = tx.outputsOfType<State>().single()
        // we check that at least one has the matching hash, the other will be the contract
        require(tx.attachments.any { it.id == state.hash }) {"At least one attachment in transaction must match hash ${state.hash}"}
    }

    object Command : TypeOnlyCommandData()

    data class State(val hash: SecureHash.SHA256) : ContractState {
        override val participants: List<AbstractParty> = emptyList()
    }
}

const val ATTACHMENT_PROGRAM_ID = "net.corda.attachmentdemo.contracts.AttachmentContract"
