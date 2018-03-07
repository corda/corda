package net.corda.behave.scenarios.helpers

import net.corda.behave.scenarios.ScenarioState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef

class Vault(state: ScenarioState) : Substeps(state) {

    fun <T: ContractState> query(nodeName: String, contractStateType: Class<out T>):  List<StateAndRef<T>>{
        return withClient(nodeName) {
            try {
                val results = it.vaultQuery(contractStateType)
                log.info("Vault query return results: $results")
                return@withClient results.states
            } catch (ex: Exception) {
                log.warn("Failed to retrieve cash configuration data", ex)
                throw ex
            }
        }
    }
}