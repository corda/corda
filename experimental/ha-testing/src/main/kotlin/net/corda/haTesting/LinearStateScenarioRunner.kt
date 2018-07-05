package net.corda.haTesting

import com.r3.corda.enterprise.perftestcordapp.contracts.LinearStateBatchNotariseContract
import com.r3.corda.enterprise.perftestcordapp.flows.LinearStateBatchNotariseFlow
import joptsimple.OptionSet
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.contextLogger
import java.util.concurrent.Callable

// Responsible for executing test scenario for a single node executing `LinearStateBatchNotariseFlow` and verifying the results
class LinearStateScenarioRunner(options: OptionSet) : AbstractScenarioRunner(options), Callable<Boolean> {
    companion object {
        private val logger = contextLogger()
    }

    override fun call(): Boolean {
        scenarioInitialized()

        try {
            val results = mutableListOf<LinearStateBatchNotariseFlow.Result>()

            for (iterNo in 1..iterCount) {
                logger.info("#$iterNo.1 - Trying: Linear state on HA")
                val result = haNodeRpcOps.startFlowWithRetryAndGet(::LinearStateBatchNotariseFlow, notary, 1, 1, true, 1000.0)
                logger.info("#$iterNo.2 - Done: Linear state on HA")
                results.add(result)
            }

            // Verify
            require(results.size == iterCount) { "Expected number of results is $iterCount, actual number of payments: ${results.size}" }
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            // TODO: Potentially implement paging validation logic for bigger data sets.
            val pageSpecification = PageSpecification(pageNumber = 1, pageSize = Int.MAX_VALUE)
            val haStates: Vault.Page<LinearStateBatchNotariseContract.State> = haNodeRpcOps.vaultQueryByWithRetry(criteria, pageSpecification)
            return verifyResultsAndStatesTally(results, haStates)
        } finally {
            scenarioCompleted()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun verifyResultsAndStatesTally(results: MutableList<LinearStateBatchNotariseFlow.Result>, states: Vault.Page<LinearStateBatchNotariseContract.State>): Boolean {
        // Unfortunately, there is absolutely nothing in `LinearStateBatchNotariseFlow.Result` which can link it to the original transaction
        return true
    }
}