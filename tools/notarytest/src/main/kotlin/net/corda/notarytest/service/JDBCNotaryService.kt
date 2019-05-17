package net.corda.notarytest.service

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.utilities.seconds
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.nodeapi.internal.config.parseAs
import net.corda.notary.mysql.MySQLNotaryConfig
import net.corda.notary.mysql.MySQLUniquenessProvider
import net.corda.notarytest.flows.AsyncLoadTestFlow
import java.security.PublicKey

class JDBCNotaryService(override val services: ServiceHubInternal, override val notaryIdentityKey: PublicKey) : SinglePartyNotaryService() {
    private val notaryConfig = services.configuration.notary
            ?: throw IllegalArgumentException("Failed to register ${this::class.java}: notary configuration not present")

    override val uniquenessProvider: MySQLUniquenessProvider = createUniquenessProvider()
    private val etaMessageThreshold = 10.seconds

    override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = NonValidatingNotaryFlow(otherPartySession, this, etaMessageThreshold)

    override fun start() {
        uniquenessProvider.createTable()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }

    private fun createUniquenessProvider(): MySQLUniquenessProvider {
        val mysqlConfig = notaryConfig.extraConfig!!.parseAs<MySQLNotaryConfig>()
        return MySQLUniquenessProvider(services.monitoringService.metrics, services.clock, mysqlConfig)
    }
}

@StartableByRPC
class JDBCLoadTestFlow(transactionCount: Int,
                       batchSize: Int,
                       inputStateCount: Int?
) : AsyncLoadTestFlow<JDBCNotaryService>(JDBCNotaryService::class.java, transactionCount, batchSize, inputStateCount)
