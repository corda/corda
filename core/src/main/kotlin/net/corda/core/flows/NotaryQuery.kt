package net.corda.core.flows

import net.corda.core.contracts.StateRef
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Class which encapsulates functionality relating to notary queries supported by the
 * notary query flows. Three types of classes reside within this class:
 *
 * - [Request]: Defines query criteria / arguments
 * - [Result]: Defines the shape of data returned by a query
 * - Other classes: Classes that capture nested data structures within a [Request] /
 *   [Result] class. For example, details of a specific event for a list of events
 *   returned as a [Result]
 *
 * To implement a new query, you must:
 *
 * - Define a new [Request] and [Result] subclass
 * - Define a new client side flow
 * - Add corresponding handling for the new subclasses within the notary services that
 *   you want to support the query.
 *
 */
class NotaryQuery {
    sealed class Request {
        @CordaSerializable
        data class SpentStates(val stateRef: StateRef) : Request()
    }

    sealed class Result {
        @CordaSerializable
        data class SpentStates(val spendEvents: List<SpendEventDetails>): Result()
    }

    @CordaSerializable
    data class SpendEventDetails(val requestTimestamp: Instant,
                                 val transactionId: String,
                                 val result: String,
                                 val requestingPartyName: String?,
                                 val workerNodeX500Name: String?)
}
