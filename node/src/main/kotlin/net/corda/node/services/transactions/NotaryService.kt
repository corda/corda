package net.corda.node.services.transactions

import net.corda.core.crypto.Party
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.api.ServiceHubInternal
import net.corda.protocols.NotaryProtocol
import kotlin.reflect.KClass

/**
 * A Notary service acts as the final signer of a transaction ensuring two things:
 * - The (optional) timestamp of the transaction is valid.
 * - None of the referenced input states have previously been consumed by a transaction signed by this Notary
 *O
 * A transaction has to be signed by a Notary to be considered valid (except for output-only transactions without a timestamp).
 *
 * This is the base implementation that can be customised with specific Notary transaction commit protocol.
 */
abstract class NotaryService(services: ServiceHubInternal) : SingletonSerializeAsToken() {

    init {
        services.registerProtocolInitiator(NotaryProtocol.Client::class) { createProtocol(it) }
    }

    /** Implement a factory that specifies the transaction commit protocol for the notary service to use */
    abstract fun createProtocol(otherParty: Party): NotaryProtocol.Service

}
