package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock

class RpcSteps(state: ScenarioState) : StepsBlock(state) {

    override fun initialize() {
        Then<String>("^user can connect to node (\\w+) using RPC$") { name ->
            withClient(name) {
                succeed()
            }
        }
    }
}
