package com.r3.corda.enterprise.perftestcordapp.flows

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateRef
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.node.services.Permissions
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.User
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.setDriverSerialization
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test

@Ignore("Use to test no-selection locally")
class CheckAllTheTestFlows {
    companion object {
        var alice: NodeHandle? = null
        private val aliceUser = User("A", "A", setOf(
                Permissions.startFlow("*")))

        var bob: NodeHandle? = null

        private val serializationEnv = setDriverSerialization()
        private val driverParameters = DriverParameters(
                startNodesInProcess = true,
                extraCordappPackagesToScan = listOf("com.r3.corda.enterprise.perftestcordapp"),
                portAllocation = incrementalPortAllocation()
        )
        val driver = DriverDSLImpl(
                portAllocation = driverParameters.portAllocation,
                debugPortAllocation = driverParameters.debugPortAllocation,
                systemProperties = driverParameters.systemProperties,
                driverDirectory = driverParameters.driverDirectory.toAbsolutePath(),
                useTestClock = driverParameters.useTestClock,
                isDebug = driverParameters.isDebug,
                startNodesInProcess = driverParameters.startNodesInProcess,
                waitForAllNodesToFinish = driverParameters.waitForAllNodesToFinish,
                extraCordappPackagesToScan = driverParameters.extraCordappPackagesToScan,
                notarySpecs = driverParameters.notarySpecs,
                jmxPolicy = driverParameters.jmxPolicy,
                compatibilityZone = null,
                networkParameters = driverParameters.networkParameters,
                notaryCustomOverrides = driverParameters.notaryCustomOverrides,
                inMemoryDB = driverParameters.inMemoryDB,
                cordappsForAllNodes = uncheckedCast(driverParameters.cordappsForAllNodes),
                enableSNI = driverParameters.enableSNI
        )

        @JvmStatic
        @BeforeClass
        fun classSetup() {
            driver.start()
            driver.run {
                alice = startNode(rpcUsers = listOf(aliceUser)).get()
                bob = startNode().get()
            }
        }

        @JvmStatic
        @AfterClass
        fun classTearDown() {
            driver.shutdown()
            serializationEnv?.close()
        }
    }

    @Test
    fun CheckCashIssueAndPaymentFlow() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueAndPaymentFlow, 1.DOLLARS, OpaqueBytes.of(0), bob!!.nodeInfo.legalIdentities[0], false, defaultNotaryIdentity)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun CheckCashIssueAndPaymentNoSelection() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueAndPaymentNoSelection, 1.DOLLARS, OpaqueBytes.of(0), bob!!.nodeInfo.legalIdentities[0], false, defaultNotaryIdentity)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestCashIssueAndDuplicatePayment() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueAndDuplicatePayment, 1.DOLLARS, OpaqueBytes.of(0), bob!!.nodeInfo.legalIdentities[0], false, defaultNotaryIdentity)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestCashIssueAndDuplicateAnonymousPayment() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueAndDuplicatePayment, 1.DOLLARS, OpaqueBytes.of(0), bob!!.nodeInfo.legalIdentities[0], false, defaultNotaryIdentity)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestCashIssueAndDoublePayment() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueAndDoublePayment, 1.DOLLARS, OpaqueBytes.of(0), bob!!.nodeInfo.legalIdentities[0], false, defaultNotaryIdentity)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestCashIssueAndDoubleAnonymousPayment() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueAndDoublePayment, 1.DOLLARS, OpaqueBytes.of(0), bob!!.nodeInfo.legalIdentities[0], true, defaultNotaryIdentity)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestCashIssueFlowAndPayKnownStates() {
        driver.run {
            val result = CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.getOrThrow()
            }
            val inputStates = setOf(StateRef(result.id, 0))

            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashPaymentFromKnownStatesFlow, inputStates, 1, 1, 1.DOLLARS, bob!!.nodeInfo.legalIdentities[0], false)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestCashIssueFlowAndPayAnonymousKnownStates() {
        driver.run {
            val result = CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.getOrThrow()
            }
            val inputStates = setOf(StateRef(result.id, 0))

            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashPaymentFromKnownStatesFlow, inputStates, 1, 1, 1.DOLLARS, bob!!.nodeInfo.legalIdentities[0], true)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestCashPaymentFlow() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.getOrThrow()
            }

            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashPaymentFlow, 1.DOLLARS, bob!!.nodeInfo.legalIdentities[0], false).returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestEmptyFlow() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::EmptyFlow)
            }
        }
    }

    @Test
    fun TestCashExitFlow() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.getOrThrow()
            }

            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashExitFlow, 1.DOLLARS, OpaqueBytes.of(0)).returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun TestLinearBatchNotarisationFlow() {
        driver.run {
            CordaRPCClient(alice!!.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::LinearStateBatchNotariseFlow, defaultNotaryIdentity, 2, 5, false, 1.0)
            }
        }
    }
}
