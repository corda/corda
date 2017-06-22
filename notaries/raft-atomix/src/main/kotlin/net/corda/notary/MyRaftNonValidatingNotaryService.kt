package net.corda.notary

import co.paralleluniverse.fibers.Suspendable
import com.google.common.net.HostAndPort
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.NotaryFlow
import net.corda.flows.TransactionParts
import net.corda.node.services.PluginService
import net.corda.node.services.PluginServiceFactory
import net.corda.node.services.PluginServiceHub
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.services.transactions.RaftUniquenessProvider
import java.util.*

/** This is an example of a custom notary service that can be provided as Cordapp */
class MyRaftNonValidatingNotaryService(val timeWindowChecker: TimeWindowChecker, val uniquenessProvider: RaftUniquenessProvider) : PluginService() {
    companion object {
        val type = SimpleNotaryService.type.getSubType("raft.custom")
    }

    object Factory : PluginServiceFactory<MyRaftNonValidatingNotaryService> {
        override fun create(services: PluginServiceHub, serializationContext: MutableList<Any>): MyRaftNonValidatingNotaryService {
            val notaryConfig = MyNotaryConfig(services.config.plugins)
            val timeWindowChecker = TimeWindowChecker(services.clock)
            val uniquenessProvider = RaftUniquenessProvider(
                    services.config.baseDirectory,
                    notaryConfig.notaryNodeAddress,
                    notaryConfig.notaryClusterAddresses,
                    services.db,
                    services.config
            )
            serializationContext.add(uniquenessProvider)
            return MyRaftNonValidatingNotaryService(timeWindowChecker, uniquenessProvider)
        }
    }

    override fun start() {
        uniquenessProvider.start()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}

@InitiatedBy(NotaryFlow.Client::class)
class MyRaftNotaryFlow(otherSide: Party) : NotaryFlow.Service(otherSide) {
    override val uniquenessProvider: UniquenessProvider
        get() = serviceHub.cordaService(MyRaftNonValidatingNotaryService::class.java).uniquenessProvider
    override val timeWindowChecker: TimeWindowChecker
        get() = serviceHub.cordaService(MyRaftNonValidatingNotaryService::class.java).timeWindowChecker

    @Suspendable
    override fun receiveAndVerifyTx(): TransactionParts {
        val ftx = receive<FilteredTransaction>(otherSide).unwrap {
            it.verify()
            it
        }
        return TransactionParts(ftx.rootHash, ftx.filteredLeaves.inputs, ftx.filteredLeaves.timeWindow)
    }
}

data class MyNotaryConfig(val properties: Properties) {
    companion object {
        val prefix = "raft.custom"
    }

    private fun getProperty(name: String) = properties.getProperty("${prefix}.$name")

    val notaryNodeAddress: HostAndPort = HostAndPort.fromString(getProperty("notaryNodeAddress"))
    val notaryClusterAddresses: List<HostAndPort> = getProperty("notaryClusterAddresses").split(",").map { HostAndPort.fromString(it) }
}

class NotaryPlugin : CordaPluginRegistry()
