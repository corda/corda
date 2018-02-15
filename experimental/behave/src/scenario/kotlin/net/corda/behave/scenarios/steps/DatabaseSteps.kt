package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock

fun databaseSteps(steps: StepsBlock) = steps {

    Then<String>("^user can connect to the database of node (\\w+)$") { name ->
        withNetwork {
            database.canConnectTo(name)
        }
    }

}
