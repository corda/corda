package net.corda.node.services.transactions

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.seconds
import net.corda.node.services.api.ServiceHubInternal
import java.security.PublicKey

/** An embedded notary service that uses the node's database to store committed states. */
class SimpleNotaryService(override val services: ServiceHubInternal, override val notaryIdentityKey: PublicKey) : SinglePartyNotaryService() {
    private val notaryConfig = services.configuration.notary
            ?: throw IllegalArgumentException("Failed to register ${this::class.java}: notary configuration not present")

    init {
        val mode = if (notaryConfig.validating) "validating" else "non-validating"
        log.info("Starting notary in $mode mode")
    }

    override val uniquenessProvider = PersistentUniquenessProvider(services.clock, services.database, services.cacheFactory)

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryServiceFlow {
        return if (notaryConfig.validating) {
            ValidatingNotaryFlow(otherPartySession, this, notaryConfig.etaMessageThresholdSeconds.seconds)
        } else {
            NonValidatingNotaryFlow(otherPartySession, this, notaryConfig.etaMessageThresholdSeconds.seconds)
        }
    }

    override fun start() {}
    override fun stop() {}
}

// Entities used by a Notary
object NodeNotarySchema

object NodeNotarySchemaV1 : MappedSchema(schemaFamily = NodeNotarySchema.javaClass, version = 1,
        mappedTypes = listOf(PersistentUniquenessProvider.BaseComittedState::class.java,
                PersistentUniquenessProvider.Request::class.java,
                PersistentUniquenessProvider.CommittedState::class.java,
                PersistentUniquenessProvider.CommittedTransaction::class.java
        )) {
    override val migrationResource = "node-notary.changelog-master"
}
