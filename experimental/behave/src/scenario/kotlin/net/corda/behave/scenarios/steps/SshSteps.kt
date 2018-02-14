package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock

fun sshSteps(steps: StepsBlock) = steps {

    Then<String>("^user can connect to node (\\w+) using SSH$") { name ->
        withNetwork {
            ssh.canConnectTo(name)
        }
    }

}
