/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc

import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.internal.packageName
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.internal.StartedNodeWithInternals
import net.corda.node.services.Permissions
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.User
import net.corda.testing.node.internal.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

class FlowsExecutionModeRpcTest : IntegrationTest() {

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }

    @Test
    fun `persistent state survives node restart`() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing.
        // More investigation is needed to establish why.
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        val user = User("mark", "dadada", setOf(invokeRpc("setFlowsDrainingModeEnabled"), invokeRpc("isFlowsDrainingModeEnabled")))
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = true, notarySpecs = emptyList())) {
            val nodeName = {
                val nodeHandle = startNode(rpcUsers = listOf(user)).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.chooseIdentity().name
                nodeHandle.rpc.setFlowsDrainingModeEnabled(true)
                nodeHandle.stop()
                nodeName
            }()

            val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user)).getOrThrow()
            assertThat(nodeHandle.rpc.isFlowsDrainingModeEnabled()).isEqualTo(true)
            nodeHandle.stop()
        }
    }
}

class FlowsExecutionModeTests : NodeBasedTest(listOf("net.corda.finance.contracts", CashSchemaV1::class.packageName)) {

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName())
    }

    private val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    private lateinit var node: StartedNodeWithInternals
    private lateinit var client: CordaRPCClient

    @Before
    fun setup() {
        node = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        client = CordaRPCClient(node.internals.configuration.rpcOptions.address)
    }

    @Test
    fun `flows draining mode can be enabled and queried`() {
        asALoggerUser { rpcOps ->
            val newValue = true
            rpcOps.setFlowsDrainingModeEnabled(true)

            val flowsExecutionMode = rpcOps.isFlowsDrainingModeEnabled()

            assertThat(flowsExecutionMode).isEqualTo(newValue)
        }
    }

    @Test
    fun `flows draining mode can be disabled and queried`() {
        asALoggerUser { rpcOps ->
            rpcOps.setFlowsDrainingModeEnabled(true)
            val newValue = false
            rpcOps.setFlowsDrainingModeEnabled(newValue)

            val flowsExecutionMode = rpcOps.isFlowsDrainingModeEnabled()

            assertThat(flowsExecutionMode).isEqualTo(newValue)
        }
    }

    @Test
    fun `node starts with flows draining mode disabled`() {
        asALoggerUser { rpcOps ->
            val defaultStartingMode = rpcOps.isFlowsDrainingModeEnabled()

            assertThat(defaultStartingMode).isEqualTo(false)
        }
    }

    private fun login(username: String, password: String, externalTrace: Trace? = null, impersonatedActor: Actor? = null): CordaRPCConnection {
        return client.start(username, password, externalTrace, impersonatedActor)
    }

    private fun asALoggerUser(action: (CordaRPCOps) -> Unit) {
        login(rpcUser.username, rpcUser.password).use {
            action(it.proxy)
        }
    }
}
