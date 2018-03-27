package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock

class NetworkSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        When("^the network is ready$") {
            state.ensureNetworkIsRunning()
        }
    }
}
