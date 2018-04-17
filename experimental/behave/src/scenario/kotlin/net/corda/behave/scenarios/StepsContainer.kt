package net.corda.behave.scenarios

import cucumber.api.java8.En
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.api.StepsProvider
import net.corda.behave.scenarios.steps.*
import net.corda.core.internal.objectOrNewInstance
import net.corda.core.utilities.loggerFor

@Suppress("KDocMissingDocumentation")
class StepsContainer(val state: ScenarioState) : En {

    companion object {
         val stepsProviders: List<StepsProvider> by lazy {
            FastClasspathScanner().addClassLoader(this::class.java.classLoader).scan()
                    .getNamesOfClassesImplementing(StepsProvider::class.java)
                    .mapNotNull { this::class.java.classLoader.loadClass(it).asSubclass(StepsProvider::class.java) }
                    .map { it.kotlin.objectOrNewInstance() }
        }
    }

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
        stepsProviders.forEach { stepsProvider ->
            val stepsDefinition = stepsProvider.stepsDefinition
            log.info("Registering: $stepsDefinition")
            stepsDefinition.initialize(state)
        }
    }

    fun steps(action: (StepsContainer.() -> Unit)) {
        action(this)
    }
}
