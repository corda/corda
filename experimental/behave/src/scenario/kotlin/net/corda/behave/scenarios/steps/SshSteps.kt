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
