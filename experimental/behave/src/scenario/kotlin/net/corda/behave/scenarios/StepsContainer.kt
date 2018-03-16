package net.corda.behave.scenarios

import cucumber.api.java8.En
import net.corda.behave.scenarios.helpers.*
//import net.corda.behave.scenarios.helpers.cordapps.Options
import net.corda.behave.scenarios.helpers.cordapps.SIMMValuation
import net.corda.behave.scenarios.steps.*
//import net.corda.behave.scenarios.steps.cordapp.optionsSteps
import net.corda.behave.scenarios.steps.cordapp.simmValuationSteps
import net.corda.core.messaging.CordaRPCOps
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("KDocMissingDocumentation")
class StepsContainer(val state: ScenarioState) : En {

    private val log: Logger = LoggerFactory.getLogger(StepsContainer::class.java)

    private val stepDefinitions: List<(StepsBlock) -> Unit> = listOf(
            ::cashSteps,
            ::configurationSteps,
            ::databaseSteps,
            ::networkSteps,
            ::rpcSteps,
            ::sshSteps,
            ::startupSteps,
            ::vaultSteps,
            ::simmValuationSteps
//            ::optionsSteps
    )

    init {
        stepDefinitions.forEach { it({ this.steps(it) }) }
    }

    fun succeed() = log.info("Step succeeded")

    fun fail(message: String) = state.fail(message)

    fun<T> error(message: String) = state.error<T>(message)

    fun node(name: String) = state.nodeBuilder(name)

    fun withNetwork(action: ScenarioState.() -> Unit) {
        state.withNetwork(action)
    }

    fun <T> withClient(nodeName: String, action: (CordaRPCOps) -> T): T {
        return state.withClient(nodeName, action)
    }

    val startup = Startup(state)

    val database = Database(state)

    val ssh = Ssh(state)

    val vault = Vault(state)

    val cash = Cash(state)

    val simmValuation = SIMMValuation(state)

//    val options = Options(state)

    private fun steps(action: (StepsContainer.() -> Unit)) {
        action(this)
    }

}
