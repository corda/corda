package com.r3corda.node.services.transactions

import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.NotaryProtocol
import com.r3corda.protocols.NotaryProtocol.TOPIC

/**
 * A Notary service acts as the final signer of a transaction ensuring two things:
 * - The (optional) timestamp of the transaction is valid.
 * - None of the referenced input states have previously been consumed by a transaction signed by this Notary
 *O
 * A transaction has to be signed by a Notary to be considered valid (except for output-only transactions without a timestamp).
 *
 * This is the base implementation that can be customised with specific Notary transaction commit protocol.
 */
abstract class NotaryService(services: ServiceHubInternal,
                             val timestampChecker: TimestampChecker,
                             val uniquenessProvider: UniquenessProvider) : AbstractNodeService(services) {
    // Do not specify this as an advertised service. Use a concrete implementation.
    // TODO: We do not want a service type that cannot be used. Fix the type system abuse here.
    object Type : ServiceType("corda.notary")

    abstract val logger: org.slf4j.Logger

    /** Implement a factory that specifies the transaction commit protocol for the notary service to use */
    abstract val protocolFactory: NotaryProtocol.Factory

    init {
        addProtocolHandler(TOPIC, TOPIC) { req: NotaryProtocol.Handshake ->
            protocolFactory.create(req.replyToParty, timestampChecker, uniquenessProvider)
        }
    }

}
