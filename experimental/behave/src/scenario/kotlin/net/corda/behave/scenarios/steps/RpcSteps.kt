package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock

fun rpcSteps(steps: StepsBlock) = steps {

    Then<String>("^user can connect to node (\\w+) using RPC$") { name ->
        withClient(name) {
            succeed()
        }
    }

}
