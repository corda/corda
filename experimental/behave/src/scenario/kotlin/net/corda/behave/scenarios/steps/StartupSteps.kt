package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.helpers.Startup

class StartupSteps(state: ScenarioState) : StepsBlock(state) {

    override fun initialize() {
        val startup = Startup(state)

        Then<String>("^user can retrieve database details for node (\\w+)$") { name ->
            withNetwork {
                startup.hasDatabaseDetails(name)
            }
        }

        Then<String>("^user can retrieve logging information for node (\\w+)$") { name ->
            withNetwork {
                startup.hasLoggingInformation(name)
            }
        }

        Then<String, String>("^node (\\w+) is on release version ([^ ]+)$") { name, version ->
            withNetwork {
                startup.hasVersion(name, version)
            }
        }

        Then<String, String>("^node (\\w+) is on platform version (\\w+)$") { name, platformVersion ->
            withNetwork {
                startup.hasPlatformVersion(name, platformVersion.toInt())
            }
        }

        Then<String>("^user can retrieve node identity information for node (\\w+)") { name ->
            withNetwork {
                startup.hasIdentityDetails(name)
            }
        }

        Then<String, String>("^node (\\w+) has loaded app (.+)$") { name, cordapp ->
            withNetwork {
                startup.hasLoadedCordapp(name, cordapp)
            }
        }

        Then<String, String>("^node (\\w+) can run (\\w+)\$") { name, cordapp ->
            startup.runCordapp(name, cordapp)
        }

        Then<String, String, String>("^node (\\w+) can run (\\w+) (\\w+)\$") { name, cordapp, arg1 ->
            startup.runCordapp(name, cordapp, arg1)
        }

        Then<String, String, String, String>("^node (\\w+) can run (\\w+) (\\w+) (\\w+)\$") { name, cordapp, arg1, arg2 ->
            startup.runCordapp(name, cordapp, arg1, arg2)
        }
    }
}