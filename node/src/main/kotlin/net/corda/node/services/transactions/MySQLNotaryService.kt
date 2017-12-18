package net.corda.node.services.transactions

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.node.services.api.ServiceHubInternal
import java.security.PublicKey
import java.util.*

/** Notary service backed by a replicated MySQL database. */
abstract class MySQLNotaryService(
        final override val services: ServiceHubInternal,
        override val notaryIdentityKey: PublicKey,
        dataSourceProperties: Properties,
        /** Database table will be automatically created in dev mode */
        val devMode: Boolean) : TrustedAuthorityNotaryService() {

    override val timeWindowChecker = TimeWindowChecker(services.clock)
    override val uniquenessProvider = MySQLUniquenessProvider(
            services.monitoringService.metrics,
            dataSourceProperties
    )

    override fun start() {
        if (devMode) uniquenessProvider.createTable()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}

class MySQLNonValidatingNotaryService(services: ServiceHubInternal,
                                      notaryIdentityKey: PublicKey,
                                      dataSourceProperties: Properties,
                                      devMode: Boolean = false) : MySQLNotaryService(services, notaryIdentityKey, dataSourceProperties, devMode) {
    companion object {
        val id = constructId(validating = false, mysql = true)
    }
    override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = NonValidatingNotaryFlow(otherPartySession, this)
}

class MySQLValidatingNotaryService(services: ServiceHubInternal,
                                      notaryIdentityKey: PublicKey,
                                      dataSourceProperties: Properties,
                                      devMode: Boolean = false) : MySQLNotaryService(services, notaryIdentityKey, dataSourceProperties, devMode) {
    companion object {
        val id = constructId(validating = true, mysql = true)
    }
    override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = ValidatingNotaryFlow(otherPartySession, this)
}