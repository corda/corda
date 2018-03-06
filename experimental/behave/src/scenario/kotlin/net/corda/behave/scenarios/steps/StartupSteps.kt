package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock

fun startupSteps(steps: StepsBlock) = steps {

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

    Then<String, String>("^node (\\w+) is on version ([^ ]+)$") { name, version ->
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

}