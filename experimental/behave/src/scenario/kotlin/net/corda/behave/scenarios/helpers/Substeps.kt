package net.corda.behave.scenarios.helpers

import net.corda.behave.scenarios.ScenarioState
import net.corda.core.messaging.CordaRPCOps
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class Substeps(protected val state: ScenarioState) {
    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    protected fun withNetwork(action: ScenarioState.() -> Unit) = state.withNetwork(action)

    protected fun <T> withClient(nodeName: String, action: ScenarioState.(CordaRPCOps) -> T): T {
        return state.withClient(nodeName) {
            try {
                action(state, it)
            } catch (ex: Exception) {
                state.error(ex.message ?: "Failed to execute RPC call")
            }
        }
    }

    protected fun <T> withClientProxy(nodeName: String, action: ScenarioState.(CordaRPCOps) -> T): T {
        return state.withClientProxy(nodeName) {
            try {
                action(state, it)
            } catch (ex: Exception) {
                state.error(ex.message ?: "Failed to execute HTTP call")
            }
        }
    }
}
