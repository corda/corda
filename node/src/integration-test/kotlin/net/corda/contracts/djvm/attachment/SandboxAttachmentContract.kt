package net.corda.contracts.djvm.attachment

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.io.ByteArrayOutputStream

class SandboxAttachmentContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val attachments = tx.attachments
        require(attachments.isNotEmpty()) { "Attachments are missing for TX=${tx.id}" }

        require(attachments.size == 1) { "Did not expect to find ${attachments.size} attachments for TX=${tx.id}" }
        val attachment = attachments[0]
        require(attachment.size > 0) { "Attachment ${attachment.id} has no contents for TX=${tx.id}" }

        val keyCount = attachment.signerKeys.size
        require(keyCount == 1) { "Did not expect to find $keyCount signing keys for attachment ${attachment.id}, TX=${tx.id}" }

        tx.commandsOfType<ExtractFile>().forEach { extract ->
            val fileName = extract.value.fileName
            val contents = ByteArrayOutputStream().use {
                attachment.extractFile(fileName, it)
                it
            }.toByteArray()
            require(contents.isNotEmpty()) { "File $fileName has no contents for TX=${tx.id}" }
        }
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class State(val issuer: AbstractParty) : ContractState {
        override val participants: List<AbstractParty> = listOf(issuer)
    }

    class ExtractFile(val fileName: String) : CommandData
}