package net.corda.notaryhealthcheck

import net.corda.core.messaging.startFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions
import net.corda.notaryhealthcheck.cordapp.*
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.DummyClusterSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class TestNotaryTypes(val validating: Boolean, @Suppress("UNUSED_PARAMETER") description: String) {

    companion object {
        @Parameterized.Parameters(name = "{1}")
        @JvmStatic
        fun validating() = listOf(arrayOf(true, "validating notary"), arrayOf(false, "non-validating notary"))
    }

    @Test
    fun testRaftNotary() {
        val testUser = User("test", "test", permissions = setOf(
                Permissions.startFlow<StartAllChecksFlow>(),
                Permissions.startFlow<StopAllChecksFlow>(),
                Permissions.invokeRpc("vaultQueryBy")))

        driver(DriverParameters(
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = validating, cluster = DummyClusterSpec(3))),
                extraCordappPackagesToScan = listOf("net.corda.notaryhealthcheck.contract", "net.corda.notaryhealthcheck.cordapp"),
                startNodesInProcess = true
        )) {
            val nodeA = startNode(rpcUsers = listOf(testUser)).getOrThrow()
            nodeA.rpc.startFlow(::StartAllChecksFlow, 2, 5).returnValue.getOrThrow()
            Thread.sleep(5.seconds.toMillis())
            val pendingStates = nodeA.rpc.vaultQueryBy(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), contractStateType = ScheduledCheckState::class.java, paging = PageSpecification(1, 100), sorting = Sort(columns = emptyList())).states
            assertEquals(4, pendingStates.size)
            val successStates = nodeA.rpc.vaultQueryBy(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), contractStateType = SuccessfulCheckState::class.java, paging = PageSpecification(1, 100), sorting = Sort(columns = emptyList())).states
            assertTrue(successStates.size > 7, "Expecting at least 8 successful checks by now, got ${successStates.size}")
            val failStates = nodeA.rpc.vaultQueryBy(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), contractStateType = FailedCheckState::class.java, paging = PageSpecification(1, 100), sorting = Sort(columns = emptyList())).states
            assertTrue(failStates.isEmpty(), "Did not expect any checks to fail, got ${failStates.size}")
            nodeA.rpc.startFlow(::StopAllChecksFlow).returnValue.getOrThrow()
            val pendingStatesAfterClean = nodeA.rpc.vaultQueryBy(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), contractStateType = ScheduledCheckState::class.java, paging = PageSpecification(1, 100), sorting = Sort(columns = emptyList())).states
            assertTrue(pendingStatesAfterClean.isEmpty(), "Expected all pending states to be cleared, got ${pendingStatesAfterClean.size}")
        }
    }

    @Test
    fun testSimpleNotary() {
        val testUser = User("test", "test", permissions = setOf(
                Permissions.startFlow<StartAllChecksFlow>(),
                Permissions.startFlow<StopAllChecksFlow>(),
                Permissions.invokeRpc("vaultQueryBy")))

        driver(DriverParameters(
                notarySpecs = listOf(NotarySpec(name = DUMMY_NOTARY_NAME, validating = validating)),
                extraCordappPackagesToScan = listOf("net.corda.notaryhealthcheck.contract", "net.corda.notaryhealthcheck.cordapp"),
                startNodesInProcess = true)) {
            val nodeA = startNode(rpcUsers = listOf(testUser)).getOrThrow()
            nodeA.rpc.startFlow(::StartAllChecksFlow, 2, 5).returnValue.getOrThrow()
            Thread.sleep(5.seconds.toMillis())
            val pendingStates = nodeA.rpc.vaultQueryBy(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), contractStateType = ScheduledCheckState::class.java, paging = PageSpecification(1, 100), sorting = Sort(columns = emptyList())).states
            assertEquals(1, pendingStates.size)
            val successStates = nodeA.rpc.vaultQueryBy(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), contractStateType = SuccessfulCheckState::class.java, paging = PageSpecification(1, 100), sorting = Sort(columns = emptyList())).states
            assertTrue(successStates.size > 1, "Expecting at least 2 successful checks by now, got ${successStates.size}")
            val failStates = nodeA.rpc.vaultQueryBy(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), contractStateType = FailedCheckState::class.java, paging = PageSpecification(1, 100), sorting = Sort(columns = emptyList())).states
            assertTrue(failStates.isEmpty(), "Did not expect any checks to fail, got ${failStates.size}")
            nodeA.rpc.startFlow(::StopAllChecksFlow).returnValue.getOrThrow()
            val pendingStatesAfterClean = nodeA.rpc.vaultQueryBy(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED), contractStateType = ScheduledCheckState::class.java, paging = PageSpecification(1, 100), sorting = Sort(columns = emptyList())).states
            assertTrue(pendingStatesAfterClean.isEmpty(), "Expected all pending states to be cleared, got ${pendingStatesAfterClean.size}")
        }

    }
}
