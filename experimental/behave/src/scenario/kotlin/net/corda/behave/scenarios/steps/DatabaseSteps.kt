package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.helpers.Database

class DatabaseSteps(state: ScenarioState) : StepsBlock(state) {

    override fun initialize() {
        val database = Database(state)

        Then<String>("^user can connect to the database of node (\\w+)$") { name ->
            withNetwork {
                database.canConnectTo(name)
            }
        }
    }
}
