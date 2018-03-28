package net.corda.behave.scenarios

import cucumber.api.java8.En
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.api.StepsProvider
import net.corda.behave.scenarios.steps.*
import net.corda.core.utilities.loggerFor
import org.reflections.Reflections

@Suppress("KDocMissingDocumentation")
class StepsContainer(val state: ScenarioState) : En {

    private val log = loggerFor<StepsContainer>()

    private val stepDefinitions: List<StepsBlock> = listOf(
            CashSteps(),
            ConfigurationSteps(),
            DatabaseSteps(),
            NetworkSteps(),
            RpcSteps(),
            SshSteps(),
            StartupSteps(),
            VaultSteps()
    )

    init {
        log.info("Initialising common Steps Provider ...")
        stepDefinitions.forEach { it.initialize(state) }

        log.info("Searching and registering custom Steps Providers ...")
        // TODO: revisit with regex package specification (eg. **/scenario/**) using http://software.clapper.org/javautil/api/org/clapper/util/classutil/ClassFinder.html
        val reflections = Reflections("net.corda")
        val foundProviders = mutableListOf<String>()
        reflections.getSubTypesOf(StepsProvider::class.java).forEach {
            foundProviders.add(it.simpleName)
            val instance = it.newInstance()
            val name = instance.name
            val stepsDefinition = instance.stepsDefinition
            assert(it.simpleName.contains(name))
            println("Registering: $stepsDefinition")
            stepsDefinition.initialize(state)
        }
    }

    fun steps(action: (StepsContainer.() -> Unit)) {
        action(this)
    }
}
