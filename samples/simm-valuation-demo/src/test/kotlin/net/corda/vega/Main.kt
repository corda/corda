/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.vega

import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_BANK_C_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver

/**
 * Sample main used for running within an IDE. Starts 4 nodes (A, B, C and Notary/Controller) as an alternative to running via gradle
 * This does not start any tests but has the nodes running in preparation for a live web demo or to receive commands
 * via the web api.
 */
fun main(args: Array<String>) {
    driver(DriverParameters(isDebug = true, waitForAllNodesToFinish = true)) {
        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = DUMMY_BANK_A_NAME),
                startNode(providedName = DUMMY_BANK_B_NAME),
                startNode(providedName = DUMMY_BANK_C_NAME)
        ).map { it.getOrThrow() }

        startWebserver(nodeA)
        startWebserver(nodeB)
        startWebserver(nodeC)
    }
}
