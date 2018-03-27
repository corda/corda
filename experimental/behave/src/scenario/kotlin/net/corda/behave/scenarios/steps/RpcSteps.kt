package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock

class RpcSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        Then<String>("^user can connect to node (\\w+) using RPC$") { name ->
            state.withClient(name) {
                succeed()
            }
        }
    }
}
