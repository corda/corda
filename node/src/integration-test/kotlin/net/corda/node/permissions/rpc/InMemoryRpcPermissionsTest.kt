package net.corda.node.permissions.rpc

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.PermissionException
import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.proxy.NodeHealthCheckRpcOps
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.NodeWithInfo
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.node.internal.NodeBasedTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class InMemoryRpcPermissionsTest : NodeBasedTest(listOf("net.corda.finance"), notaries = listOf(DUMMY_NOTARY_NAME)) {

    private lateinit var node: NodeWithInfo
    private lateinit var client: CordaRPCClient

    @Before
    override fun setUp() {
        super.setUp()

        val configOverrides = mapOf(
                "security" to mapOf(
                        "authService" to mapOf(
                                "dataSource" to mapOf(
                                        "type" to "INMEMORY",
                                        "users" to listOf(
                                                mapOf(
                                                        "username" to "legacyUser",
                                                        "password" to "password",
                                                        "permissions" to listOf("StartFlow.net.corda.finance.flows.CashIssueFlow",
                                                                "InvokeRpc.nodeInfo", "InvokeRpc.networkMapSnapshot")
                                                ),
                                                mapOf(
                                                        "username" to "newStyleUser",
                                                        "password" to "password",
                                                        "permissions" to listOf("StartFlow.net.corda.finance.flows.CashIssueFlow",
                                                                "InvokeRpc:net.corda.core.messaging.CordaRPCOps#ALL",
                                                                "InvokeRpc:net.corda.core.internal.messaging.InternalCordaRPCOps#getAttachmentTrustInfos",
                                                                "InvokeRpc:net.corda.client.rpc.proxy.NodeHealthCheckRpcOps#READ_ONLY")
                                                )
                                        )
                                )
                        )
                )
        )

        node = startNode(ALICE_NAME, rpcUsers = emptyList(), configOverrides = configOverrides)
        client = CordaRPCClient(node.node.configuration.rpcOptions.address)
    }

    @Test
    fun `check legacy user flow permissions are honored`() {
        client.start("legacyUser", "password").use {
            val proxy = it.proxy
            assertNotNull(proxy.startFlow(::CashIssueFlow, 10.POUNDS, OpaqueBytes("1".toByteArray()), notaryNodes.first().info.legalIdentities.first()))
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to start flow `CashExitFlow`") {
                proxy.startFlowDynamic(CashExitFlow::class.java)
            }
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to start flow `CashExitFlow`") {
                proxy.startTrackedFlowDynamic(CashExitFlow::class.java)
            }
        }
    }

    @Test
    fun `check legacy user permissions on RPC calls are honored`() {
        client.start("legacyUser", "password").use {
            val proxy = it.proxy
            assertNotNull(proxy.nodeInfo())
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to call 'clearNetworkMapCache()'") {
                proxy.clearNetworkMapCache()
            }
            assertNotNull(proxy.nodeInfo())
        }
    }

    @Test
    fun `check new style user permissions on RPC calls are honored`() {
        // Corda RPC Ops operations
        client.start("newStyleUser", "password").use {
            val proxy = it.proxy as InternalCordaRPCOps
            assertNotNull(proxy.nodeInfo())
            proxy.clearNetworkMapCache()
            proxy.attachmentTrustInfos
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to call 'dumpCheckpoints()'") {
                proxy.dumpCheckpoints()
            }
        }

        // HealthCheck operations
        RPCClient<NodeHealthCheckRpcOps>(node.node.configuration.rpcOptions.address)
            .start(NodeHealthCheckRpcOps::class.java, "newStyleUser", "password").use {
                val proxy = it.proxy
                assertNotNull(proxy.runtimeInfo())
            }
    }
}