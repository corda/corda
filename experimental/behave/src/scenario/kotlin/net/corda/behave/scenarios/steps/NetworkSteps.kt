/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
