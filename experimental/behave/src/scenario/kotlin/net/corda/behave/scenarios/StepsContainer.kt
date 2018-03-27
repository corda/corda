package net.corda.behave.scenarios

import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.api.StepsProvider
import net.corda.behave.scenarios.steps.*
import net.corda.core.utilities.loggerFor
import org.reflections.Reflections

@Suppress("KDocMissingDocumentation")
class StepsContainer(val state: ScenarioState) {

    private val log = loggerFor<StepsContainer>()

    private val stepDefinitions: List<StepsBlock> = listOf(
            CashStepsBlock(state),
            ConfigurationSteps(state),
            DatabaseSteps(state),
            NetworkSteps(state),
            RpcSteps(state),
            SshSteps(state),
            StartupSteps(state),
            VaultSteps(state)
    )

    init {
        log.info("Initialising common Steps Provider ...")
        stepDefinitions.forEach { it.initialize() }

        log.info("Searching and registering custom Steps Providers ...")
        val reflections = Reflections("net.corda")
        val foundProviders = mutableListOf<String>()
        reflections.getSubTypesOf(StepsProvider::class.java).forEach {
            foundProviders.add(it.simpleName)
            val instance = it.newInstance()
            val name = instance.name
            val stepsDefinition = instance.stepsDefinition
            assert(it.simpleName.contains(name))
            println("Registering: $stepsDefinition")
            stepsDefinition.initialize()
        }
    }

    fun steps(action: (StepsContainer.() -> Unit)) {
        action(this)
    }
}


