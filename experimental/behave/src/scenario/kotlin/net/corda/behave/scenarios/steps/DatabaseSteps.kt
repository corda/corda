package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.helpers.Database

class DatabaseSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        val database = Database(state)

        Then<String>("^user can connect to the database of node (\\w+)$") { name ->
            state.withNetwork {
                database.canConnectTo(name)
            }
        }
    }
}
