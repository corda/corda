/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.attachmentdemo

import net.corda.core.internal.div
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    val demoUser = listOf(User("demo", "demo", setOf("StartFlow.net.corda.flows.FinalityFlow")))
    driver(DriverParameters(isDebug = true, driverDirectory = "build" / "attachment-demo-nodes", waitForAllNodesToFinish = true)) {
        startNode(providedName = DUMMY_BANK_A_NAME, rpcUsers = demoUser)
        startNode(providedName = DUMMY_BANK_B_NAME, rpcUsers = demoUser)
    }
}
