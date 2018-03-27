package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock

class NetworkSteps(state: ScenarioState) : StepsBlock(state) {

    override fun initialize() {
        When("^the network is ready$") {
            state.ensureNetworkIsRunning()
        }
    }
}
