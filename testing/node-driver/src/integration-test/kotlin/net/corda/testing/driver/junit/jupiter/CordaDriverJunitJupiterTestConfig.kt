package net.corda.testing.driver.junit.jupiter

import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOC_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.internal.FINANCE_CORDAPPS

val cordaDriverJunitJupiterTestConfig = CordaDriverJunitJupiterConfig(
        parametersForNodes = listOf(
            NodeParameters(
                ALICE_NAME
            ),
            NodeParameters(
                BOC_NAME
            )
        ),
        driverParameters = DriverParameters(
            cordappsForAllNodes = FINANCE_CORDAPPS,
            isDebug = true,
            startNodesInProcess = true,
            networkParameters = testNetworkParameters(
                minimumPlatformVersion = 4
            )
        )
)