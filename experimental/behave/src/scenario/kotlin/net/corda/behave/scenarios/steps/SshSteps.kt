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
import net.corda.behave.scenarios.helpers.Ssh

class SshSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        val ssh = Ssh(state)

        Then<String>("^user can connect to node (\\w+) using SSH$") { name ->
            state.withNetwork {
                ssh.canConnectTo(name)
            }
        }
    }
}
