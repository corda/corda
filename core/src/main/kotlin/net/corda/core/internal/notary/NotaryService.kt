package net.corda.core.internal.notary

import net.corda.core.DeleteForDJVM
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.PublicKey

@DeleteForDJVM
abstract class NotaryService : SingletonSerializeAsToken() {
    abstract val services: ServiceHub
    abstract val notaryIdentityKey: PublicKey
    /** TODO: temporary workaround for POC, implement properly  */
    lateinit var rotatedKeys: Set<PublicKey>

    /**
     * Interfaces for the request and result formats of queries supported by notary services. To
     * implement a new query, you must:
     *
     * - Define data classes which implement the [Query.Request] and [Query.Result] interfaces
     * - Add corresponding handling for the new classes within the notary service implementations
     *   that you want to support the query.
     */
    interface Query {
        interface Request
        interface Result
    }

    abstract fun start()
    abstract fun stop()

    /**
     * Produces a notary service flow which has the corresponding sends and receives as [NotaryFlow.Client].
     * @param otherPartySession client [Party] making the request
     */
    abstract fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?>

    /**
     * Processes a [Query.Request] and returns a [Query.Result].
     *
     * Note that this always throws an [UnsupportedOperationException] to handle notary
     * implementations that do not support this functionality. This must be overridden by
     * notary implementations wishing to support query functionality.
     *
     * Overrides of this function may themselves still throw an [UnsupportedOperationException],
     * if they do not support specific query implementations
     */
    open fun processQuery(query: Query.Request): Query.Result {
        throw UnsupportedOperationException("Notary has not implemented query support")
    }
}