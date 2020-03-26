package net.corda.core.internal.notary

import net.corda.core.DeleteForDJVM
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.NotaryQuery
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.PublicKey

@DeleteForDJVM
abstract class NotaryService : SingletonSerializeAsToken() {
    abstract val services: ServiceHub
    abstract val notaryIdentityKey: PublicKey

    abstract fun start()
    abstract fun stop()

    /**
     * Produces a notary service flow which has the corresponding sends and receives as [NotaryFlow.Client].
     * @param otherPartySession client [Party] making the request
     */
    abstract fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?>

    /**
     * Produces a notary query flow in response to receiving an initiating client side
     * notary query flow.
     *
     * Note that this always throws an [UnsupportedOperationException] to handle notary
     * implementations that do not support this functionality. This must be overridden by
     * notary implementations wishing to support query functionality.
     *
     * @param otherPartySession client [Party] making the request
     */
    open fun createQueryFlow(otherPartySession: FlowSession): FlowLogic<Void?> {
        throw UnsupportedOperationException("Notary has not implemented query support")
    }

    /**
     * Processes a [NotaryQuery.Request] and returns a [NotaryQuery.Result].
     *
     * Note that this always throws an [UnsupportedOperationException] to handle notary
     * implementations that do not support this functionality. This must be overridden by
     * notary implementations wishing to support query functionality.
     *
     * Overrides of this function may themselves still throw an [UnsupportedOperationException],
     * as notary implementations are not obliged to support the full set of queries defined
     * within [NotaryQuery].
     */
    open fun processQuery(query: NotaryQuery.Request): NotaryQuery.Result {
        throw UnsupportedOperationException("Notary has not implemented query support")
    }
}