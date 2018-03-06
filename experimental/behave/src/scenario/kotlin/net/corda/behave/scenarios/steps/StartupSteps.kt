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

}