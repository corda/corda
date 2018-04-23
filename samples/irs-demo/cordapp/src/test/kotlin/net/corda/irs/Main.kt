/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.irs

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    driver(DriverParameters(useTestClock = true, isDebug = true, waitForAllNodesToFinish = true)) {
        val (nodeA, nodeB) = listOf(
                startNode(providedName = DUMMY_BANK_A_NAME),
                startNode(providedName = DUMMY_BANK_B_NAME),
                startNode(providedName = CordaX500Name("Regulator", "Moscow", "RU"))
        ).map { it.getOrThrow() }
        val controller = defaultNotaryNode.getOrThrow()

        startWebserver(controller)
        startWebserver(nodeA)
        startWebserver(nodeB)
    }
}
