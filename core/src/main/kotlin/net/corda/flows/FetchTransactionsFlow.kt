package net.corda.flows

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.transactions.SignedTransaction

/**
 * Given a set of tx hashes (IDs), either loads them from local disk or asks the remote peer to provide them.
 *
 * A malicious response in which the data provided by the remote peer does not hash to the requested hash results in
 * [FetchDataFlow.DownloadedVsRequestedDataMismatch] being thrown. If the remote peer doesn't have an entry, it
 * results in a [FetchDataFlow.HashNotFound] exception. Note that returned transactions are not inserted into
 * the database, because it's up to the caller to actually verify the transactions are valid.
 */
@InitiatingFlow
class FetchTransactionsFlow(requests: Set<SecureHash>, otherSide: PartyAndCertificate) :
        FetchDataFlow<SignedTransaction, SignedTransaction>(requests, otherSide) {

    override fun load(txid: SecureHash): SignedTransaction? {
        return serviceHub.storageService.validatedTransactions.getTransaction(txid)
    }
}
