package net.corda.node.services.transactions

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.node.services.api.ServiceHubInternal
import java.security.PublicKey

/** An embedded notary service that uses the node's database to store committed states. */
class SimpleNotaryService(override val services: ServiceHubInternal, override val notaryIdentityKey: PublicKey) : TrustedAuthorityNotaryService() {
    private val notaryConfig = services.configuration.notary
            ?: throw IllegalArgumentException("Failed to register ${this::class.java}: notary configuration not present")

    override val uniquenessProvider = PersistentUniquenessProvider(services.clock, services.database, services.cacheFactory)

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryServiceFlow {
        return if (notaryConfig.validating) {
            ValidatingNotaryFlow(otherPartySession, this)
        } else {
            NonValidatingNotaryFlow(otherPartySession, this)
        }
    }

    override fun start() {}
    override fun stop() {}
}

// Entities used by a Notary
object NodeNotary

object NodeNotaryV1 : MappedSchema(schemaFamily = NodeNotary.javaClass, version = 1,
        mappedTypes = listOf(PersistentUniquenessProvider.BaseComittedState::class.java,
                PersistentUniquenessProvider.Request::class.java,
                PersistentUniquenessProvider.CommittedState::class.java
        )) {
    override val migrationResource = "node-notary.changelog-master"
}
