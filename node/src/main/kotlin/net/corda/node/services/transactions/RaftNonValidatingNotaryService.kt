package net.corda.node.services.transactions

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.TimeWindowChecker
import net.corda.node.services.PluginService
import net.corda.node.services.PluginServiceFactory
import net.corda.node.services.PluginServiceHub
import net.corda.node.services.config.FullNodeConfiguration

/** A non-validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftNonValidatingNotaryService(val timeWindowChecker: TimeWindowChecker,
                                     val uniquenessProvider: RaftUniquenessProvider) : PluginService(), NotaryService {
    companion object {
        val type = SimpleNotaryService.Companion.type.getSubType("raft")
    }

    object Factory : PluginServiceFactory<RaftNonValidatingNotaryService> {
        override fun create(services: PluginServiceHub, serializationContext: MutableList<Any>): RaftNonValidatingNotaryService {
            val notaryConfig = RaftConfiguration(services.config as FullNodeConfiguration)
            val timeWindowChecker = TimeWindowChecker(services.clock)
            val uniquenessProvider = RaftUniquenessProvider(
                    services.config.baseDirectory,
                    notaryConfig.notaryNodeAddress,
                    notaryConfig.notaryClusterAddresses,
                    services.db,
                    services.config
            )
            serializationContext.add(uniquenessProvider)
            return RaftNonValidatingNotaryService(timeWindowChecker, uniquenessProvider)
        }
    }

    override val serviceFlowFactory: (Party, Int) -> FlowLogic<Void?> = { otherParty, _ ->
        NonValidatingNotaryFlow(otherParty, timeWindowChecker, uniquenessProvider)
    }

    override fun start() {
        uniquenessProvider.start()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}