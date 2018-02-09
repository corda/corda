package net.corda.behave.scenarios

import cucumber.api.java.After
import cucumber.api.java.Before

@Suppress("KDocMissingDocumentation")
class ScenarioHooks(private val state: ScenarioState) {

    @Before
    fun beforeScenario() {
    }

    @After
    fun afterScenario() {
        state.stopNetwork()
    }

}