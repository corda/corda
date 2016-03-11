/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core

import core.node.services.AttachmentStorage
import core.node.services.IdentityService
import java.io.FileNotFoundException

/**
 * Looks up identities and attachments from storage to generate a [LedgerTransaction].
 *
 * @throws FileNotFoundException if a required transaction was not found in storage.
 */
fun WireTransaction.toLedgerTransaction(identityService: IdentityService,
                                        attachmentStorage: AttachmentStorage): LedgerTransaction {
    val authenticatedArgs = commands.map {
        val institutions = it.pubkeys.mapNotNull { pk -> identityService.partyFromKey(pk) }
        AuthenticatedObject(it.pubkeys, institutions, it.data)
    }
    val attachments = attachments.map {
        attachmentStorage.openAttachment(it) ?: throw FileNotFoundException(it.toString())
    }
    return LedgerTransaction(inputs, attachments, outputs, authenticatedArgs, id)
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
