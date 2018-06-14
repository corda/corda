package net.corda.haTesting

import joptsimple.OptionSet
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds

abstract class AbstractScenarioRunner(options: OptionSet) {
    companion object {
        private val logger = contextLogger()

        @JvmStatic
        protected fun establishRpcConnection(endpoint: NetworkHostAndPort, user: String, password: String,
                                             onError: (Throwable) -> CordaRPCOps =
                                                   {
                                                       logger.error("establishRpcConnection", it)
                                                       throw it
                                                   }): CordaRPCOps {
            try {
                val retryInterval = 5.seconds

                val client = CordaRPCClient(endpoint,
                    CordaRPCClientConfiguration.DEFAULT.copy(
                        connectionMaxRetryInterval = retryInterval
                    )
                )
                val connection = client.start(user, password)
                return connection.proxy
            } catch (th: Throwable) {
                return onError(th)
            }
        }
    }

    protected val haNodeRpcOps: CordaRPCOps
    protected val normalNodeRpcOps: CordaRPCOps
    protected val haNodeParty: Party
    protected val normalNodeParty: Party
    protected val notary: Party
    protected val iterCount: Int

    init {
        haNodeRpcOps = establishRpcConnection(
                options.valueOf(MandatoryCommandLineArguments.haNodeRpcAddress.name) as NetworkHostAndPort,
                options.valueOf(MandatoryCommandLineArguments.haNodeRpcUserName.name) as String,
                options.valueOf(MandatoryCommandLineArguments.haNodeRpcPassword.name) as String
        )
        haNodeParty = haNodeRpcOps.nodeInfo().legalIdentities.first()
        normalNodeRpcOps = establishRpcConnection(
                options.valueOf(MandatoryCommandLineArguments.normalNodeRpcAddress.name) as NetworkHostAndPort,
                options.valueOf(MandatoryCommandLineArguments.normalNodeRpcUserName.name) as String,
                options.valueOf(MandatoryCommandLineArguments.normalNodeRpcPassword.name) as String
        )
        normalNodeParty = normalNodeRpcOps.nodeInfo().legalIdentities.first()
        notary = normalNodeRpcOps.notaryIdentities().first()
        iterCount = options.valueOf(OptionalCommandLineArguments.iterationsCount.name) as Int? ?: 10
        logger.info("Total number of iterations to run: $iterCount")
    }

    protected fun scenarioInitialized() {
        // TODO: start a daemon thread which will talk to HA Node and installs termination schedule to it
        // The daemon will monitor availability of HA Node and as soon as it is down and then back-up it will install
        // the next termination schedule.
    }

    protected fun scenarioCompleted() {
        // TODO: stop the daemon and dispose any other resources
    }
}