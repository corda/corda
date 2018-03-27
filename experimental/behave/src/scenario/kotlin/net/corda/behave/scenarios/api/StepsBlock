package net.corda.behave.scenarios.api

import cucumber.api.java8.En
import net.corda.behave.scenarios.ScenarioState
import net.corda.core.utilities.contextLogger

interface StepsBlock : En {

    companion object {
        val log = contextLogger()
    }

    fun initialize(state: ScenarioState)

    fun succeed() = log.info("Step succeeded")
}
