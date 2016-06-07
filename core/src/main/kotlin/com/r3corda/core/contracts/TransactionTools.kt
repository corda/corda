package com.r3corda.core.contracts

import com.r3corda.core.node.services.AttachmentStorage
import com.r3corda.core.node.services.IdentityService
import java.io.FileNotFoundException

/**
 * Looks up identities and attachments from storage to generate a [LedgerTransaction].
 *
 * @throws FileNotFoundException if a required attachment was not found in storage.
 */
fun WireTransaction.toLedgerTransaction(identityService: IdentityService,
                                        attachmentStorage: AttachmentStorage): LedgerTransaction {
    val authenticatedArgs = commands.map {
        val institutions = it.signers.mapNotNull { pk -> identityService.partyFromKey(pk) }
        AuthenticatedObject(it.signers, institutions, it.value)
    }
    val attachments = attachments.map {
        attachmentStorage.openAttachment(it) ?: throw FileNotFoundException(it.toString())
    }
    return LedgerTransaction(inputs, attachments, outputs, authenticatedArgs, id, type)
}

/**
 * Calls [verify] to check all required signatures are present, and then uses the passed [IdentityService] to call
 * [WireTransaction.toLedgerTransaction] to look up well known identities from pubkeys.
 */
fun SignedTransaction.verifyToLedgerTransaction(identityService: IdentityService,
                                                attachmentStorage: AttachmentStorage): LedgerTransaction {
    verify()
    return tx.toLedgerTransaction(identityService, attachmentStorage)
}
