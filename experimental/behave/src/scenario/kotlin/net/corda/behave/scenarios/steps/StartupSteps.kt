/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.helpers.Startup

class StartupSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        val startup = Startup(state)

        Then<String>("^user can retrieve database details for node (\\w+)$") { name ->
            state.withNetwork {
                startup.hasDatabaseDetails(name)
            }
        }

        Then<String>("^user can retrieve logging information for node (\\w+)$") { name ->
            state.withNetwork {
                startup.hasLoggingInformation(name)
            }
        }

        Then<String, String>("^node (\\w+) is on release version ([^ ]+)$") { name, version ->
            state.withNetwork {
                startup.hasVersion(name, version)
            }
        }

        Then<String, String>("^node (\\w+) is on platform version (\\w+)$") { name, platformVersion ->
            state.withNetwork {
                startup.hasPlatformVersion(name, platformVersion.toInt())
            }
        }

        Then<String>("^user can retrieve node identity information for node (\\w+)") { name ->
            state.withNetwork {
                startup.hasIdentityDetails(name)
            }
        }

        Then<String, String>("^node (\\w+) has loaded app (.+)$") { name, cordapp ->
            state.withNetwork {
                startup.hasLoadedCordapp(name, cordapp)
            }
        }

        Then<String, String>("^node (\\w+) can run (\\w+)\$") { name, cordapp ->
            state.withNetwork {
                startup.runCordapp(name, cordapp)
            }
        }

        Then<String, String, String>("^node (\\w+) can run (\\w+) (\\w+)\$") { name, cordapp, arg1 ->
            state.withNetwork {
                startup.runCordapp(name, cordapp, arg1)
            }
        }

        Then<String, String, String, String>("^node (\\w+) can run (\\w+) (\\w+) (\\w+)\$") { name, cordapp, arg1, arg2 ->
            state.withNetwork {
                startup.runCordapp(name, cordapp, arg1, arg2)
            }
        }
    }
}