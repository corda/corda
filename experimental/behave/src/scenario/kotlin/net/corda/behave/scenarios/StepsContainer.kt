package net.corda.behave.scenarios

import cucumber.api.java8.En
import net.corda.behave.scenarios.steps.dummySteps
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("KDocMissingDocumentation")
class StepsContainer(val state: ScenarioState) : En {

    val log: Logger = LoggerFactory.getLogger(StepsContainer::class.java)

    private val stepDefinitions: List<(StepsBlock) -> Unit> = listOf(
            ::dummySteps
    )

    init {
        stepDefinitions.forEach { it({ this.steps(it) }) }
    }

    private fun steps(action: (StepsContainer.() -> Unit)) {
        action(this)
    }

}
