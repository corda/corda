package net.corda.behave.scenarios

//import net.corda.behave.scenarios.helpers.cordapps.Options
//import net.corda.behave.scenarios.helpers.cordapps.SIMMValuation
//import net.corda.behave.scenarios.steps.cordapp.optionsSteps
//import net.corda.behave.scenarios.steps.cordapp.simmValuationSteps
import cucumber.api.java8.En
import net.corda.behave.scenarios.helpers.*
import net.corda.behave.scenarios.steps.*
import net.corda.core.messaging.CordaRPCOps
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("KDocMissingDocumentation")
abstract class StepsContainer(val state: ScenarioState) : En {

    private val log: Logger = LoggerFactory.getLogger(StepsContainer::class.java)

    private val stepDefinitions: List<(StepsBlock) -> Unit> = listOf(
            ::cashSteps,
            ::configurationSteps,
            ::databaseSteps,
            ::networkSteps,
            ::rpcSteps,
            ::sshSteps,
            ::startupSteps,
            ::vaultSteps
//            ::optionsSteps
    )

    init {
        println("StepsContainer init ...")
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

//    val options = Options(state)

    fun steps(action: (StepsContainer.() -> Unit)) {
        action(this)
    }

    fun registerStepsProvider(provider: StepsProvider) {
        log.info("Registering steps provider: $provider")
        provider.stepsDefinition { this.steps { provider.stepsDefinition} }
    }
}

interface StepsProvider {
    val name: String
    val stepsDefinition: (StepsBlock) -> Unit
}

class BarStepsProvider : StepsProvider {

    override val name: String
        get() = "Bar"
    override val stepsDefinition: (StepsBlock) -> Unit
        get() = {}

}
