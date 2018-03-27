//package net.corda.behave.scenarios.runtime
//
//import net.corda.behave.scenarios.ScenarioState
//import net.corda.behave.scenarios.api.StepsBlock
//import net.corda.core.messaging.CordaRPCOps
//
//class StepsBlockImpl : StepsBlock {
//
//    lateinit var state: ScenarioState
//
//    override fun initialize(_state: ScenarioState) {
//        state = _state
//    }
//
//    fun fail(message: String) = state.fail(message)
//
//    fun<T> error(message: String) = state.error<T>(message)
//
//    fun node(name: String) = state.nodeBuilder(name)
//
//    fun withNetwork(action: ScenarioState.() -> Unit) {
//        state.withNetwork(action)
//    }
//
//    fun <T> withClient(nodeName: String, action: (CordaRPCOps) -> T): T {
//        return state.withClient(nodeName, action)
//    }
//}