package net.corda.nodeapi.notary

import net.corda.core.contracts.StateRef
import net.corda.core.internal.notary.NotaryService
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Implementations of queries supported by notary services
 */
class SpentStateQuery {
    @CordaSerializable
    data class Request(val stateRef: StateRef,
                       val maxResults: Int,
                       val successOnly: Boolean,
                       val startTime: Instant?,
                       val endTime: Instant?,
                       val lastTxId: String?) : NotaryService.Query.Request

    @CordaSerializable
    data class Result(val spendEvents: List<SpendEventDetails>,
                      val moreResults: Boolean): NotaryService.Query.Result

    @CordaSerializable
    data class SpendEventDetails(val requestTimestamp: Instant,
                                 val transactionId: String,
                                 val result: String,
                                 val requestingPartyName: String?,
                                 val workerNodeX500Name: String?)
}