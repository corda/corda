package net.corda.notarytest.flows

import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.config.parseAs
import net.corda.notary.mysql.MySQLUniquenessProvider
import net.corda.notary.standalonejpa.StandaloneJPAUniquenessProvider

@StartableByRPC
class JPALoadTestFlow(
        transactionCount: Int,
        batchSize: Int = 100,
        inputStateCount: Int? = null
) : AsyncLoadTestFlow(transactionCount, batchSize, inputStateCount) {
    override fun startUniquenessProvider(serviceHubInternal: ServiceHubInternal): UniquenessProvider {
        val cordappConfig = CordappConfigFileProvider(serviceHubInternal.configuration.cordappDirectories).getConfigByName(serviceHubInternal.getAppContext().cordapp.name)
        val notaryConfig = cordappConfig.getConfig("notary").parseAs<NotaryConfig>()
        return StandaloneJPAUniquenessProvider(serviceHubInternal.monitoringService.metrics, serviceHubInternal.clock, notaryConfig.jpa!!)
    }
}