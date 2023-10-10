package net.corda.contracts.incompatible.version1

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.internal.AttachmentsClassLoader
import net.corda.core.transactions.LedgerTransaction

class AttachmentContract : Contract {

    private val FAIL_CONTRACT_VERIFY = java.lang.Boolean.getBoolean("net.corda.contracts.incompatible.AttachmentContract.fail.verify")
    override fun verify(tx: LedgerTransaction) {
        if (FAIL_CONTRACT_VERIFY) throw object:TransactionVerificationException(tx.id, "AttachmentContract verify failed.", null) {}
        val state = tx.outputsOfType<State>().single()
        // we check that at least one has the matching hash, the other will be the contract
        require(tx.attachments.any { it.id == state.hash }) {"At least one attachment in transaction must match hash ${state.hash}"}
    }

    object Command : TypeOnlyCommandData()

    data class State(val hash: SecureHash.SHA256) : ContractState {
        private val FAIL_CONTRACT_STATE = java.lang.Boolean.getBoolean("net.corda.contracts.incompatible.AttachmentContract.fail.state") && (this.javaClass.classLoader !is AttachmentsClassLoader)
        init {
            if (FAIL_CONTRACT_STATE) throw TransactionVerificationException.TransactionRequiredContractUnspecifiedException(hash,"AttachmentContract state initialisation failed.")
        }
        override val participants: List<AbstractParty> = emptyList()
    }
}

const val ATTACHMENT_PROGRAM_ID = "net.corda.contracts.incompatible.version1.AttachmentContract"
