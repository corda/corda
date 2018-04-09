package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.core.utilities.minutes

class NetworkSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        When("^the network is ready$") {
            state.ensureNetworkIsRunning()
        }

        When<Int>("^the network is ready within (\\d+) minutes$") { minutes ->
            state.ensureNetworkIsRunning(minutes.minutes)
        }
    }
}
