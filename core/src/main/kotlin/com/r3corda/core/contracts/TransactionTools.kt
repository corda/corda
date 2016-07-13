package com.r3corda.core.contracts

import com.r3corda.core.node.ServiceHub
import java.io.FileNotFoundException

// TODO: Move these into the actual classes (i.e. where people would expect to find them) and split Transactions.kt into multiple files

/**
 * Looks up identities and attachments from storage to generate a [LedgerTransaction]. A transaction is expected to
 * have been fully resolved using the resolution protocol by this point.
 *
 * @throws FileNotFoundException if a required attachment was not found in storage.
 * @throws TransactionResolutionException if an input points to a transaction not found in storage.
 */
fun WireTransaction.toLedgerTransaction(services: ServiceHub): LedgerTransaction {
    // Look up random keys to authenticated identities. This is just a stub placeholder and will all change in future.
    val authenticatedArgs = commands.map {
        val parties = it.signers.mapNotNull { pk -> services.identityService.partyFromKey(pk) }
        AuthenticatedObject(it.signers, parties, it.value)
    }
    // Open attachments specified in this transaction. If we haven't downloaded them, we fail.
    val attachments = attachments.map {
        services.storageService.attachments.openAttachment(it) ?: throw FileNotFoundException(it.toString())
    }
    val resolvedInputs = inputs.map { StateAndRef(services.loadState(it), it) }
    return LedgerTransaction(resolvedInputs, outputs, authenticatedArgs, attachments, id, signers, type, timestamp)
}

/**
 * Calls [verify] to check all required signatures are present, and then calls [WireTransaction.toLedgerTransaction]
 * with the passed in [ServiceHub] to resolve the dependencies, returning an unverified LedgerTransaction.
 *
 * @throws FileNotFoundException if a required attachment was not found in storage.
 * @throws TransactionResolutionException if an input points to a transaction not found in storage.
 */
fun SignedTransaction.toLedgerTransaction(services: ServiceHub): LedgerTransaction {
    verifySignatures()
    return tx.toLedgerTransaction(services)
}
