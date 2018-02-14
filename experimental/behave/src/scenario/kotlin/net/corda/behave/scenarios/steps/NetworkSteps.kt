package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock

fun networkSteps(steps: StepsBlock) = steps {

    When("^the network is ready$") {
        state.ensureNetworkIsRunning()
    }

}
