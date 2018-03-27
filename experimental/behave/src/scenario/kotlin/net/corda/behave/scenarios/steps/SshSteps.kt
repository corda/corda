package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.helpers.Ssh

class SshSteps(state: ScenarioState) : StepsBlock(state) {

    override fun initialize() {
        val ssh = Ssh(state)

        Then<String>("^user can connect to node (\\w+) using SSH$") { name ->
            withNetwork {
                ssh.canConnectTo(name)
            }
        }
    }
}
