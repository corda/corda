/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package protocols

import core.SignedTransaction
import core.crypto.SecureHash
import core.messaging.SingleMessageRecipient

/**
 * Given a set of tx hashes (IDs), either loads them from local disk or asks the remote peer to provide them.
 *
 * A malicious response in which the data provided by the remote peer does not hash to the requested hash results in
 * [FetchDataProtocol.DownloadedVsRequestedDataMismatch] being thrown. If the remote peer doesn't have an entry, it
 * results in a [FetchDataProtocol.HashNotFound] exception. Note that returned transactions are not inserted into
 * the database, because it's up to the caller to actually verify the transactions are valid.
 */
class FetchTransactionsProtocol(requests: Set<SecureHash>, otherSide: SingleMessageRecipient) :
        FetchDataProtocol<SignedTransaction, SignedTransaction>(requests, otherSide) {
    companion object {
        const val TOPIC = "platform.fetch.tx"
    }

    override fun load(txid: SecureHash): SignedTransaction? = serviceHub.storageService.validatedTransactions[txid]
    override val queryTopic: String = TOPIC
}