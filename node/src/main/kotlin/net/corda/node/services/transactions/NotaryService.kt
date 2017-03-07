package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.flows.NotaryFlow
import net.corda.node.services.api.ServiceHubInternal

/**
 * A Notary service acts as the final signer of a transaction ensuring two things:
 * - The (optional) timestamp of the transaction is valid.
 * - None of the referenced input states have previously been consumed by a transaction signed by this Notary
 *O
 * A transaction has to be signed by a Notary to be considered valid (except for output-only transactions without a timestamp).
 *
 * This is the base implementation that can be customised with specific Notary transaction commit flow.
 */
abstract class NotaryService(services: ServiceHubInternal) : SingletonSerializeAsToken() {

    init {
        services.registerFlowInitiator(NotaryFlow.Client::class) { createFlow(it) }
    }

    /** Implement a factory that specifies the transaction commit flow for the notary service to use */
    abstract fun createFlow(otherParty: Party): FlowLogic<Void?>

}
