package net.corda.extensions.node.rpc

import com.typesafe.config.Config
import net.corda.client.rpc.internal.RPCClient
import net.corda.client.rpc.proxy.NodeHealthCheckRpcOps
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.CordaTransactionSupport
import net.corda.core.utilities.Try
import net.corda.core.utilities.getOrThrow
import net.corda.ext.api.NodeInitialContext
import net.corda.ext.api.lifecycle.NodeLifecycleEvent
import net.corda.ext.api.lifecycle.NodeLifecycleObserver.Companion.reportSuccess
import net.corda.ext.api.NodeServicesContext
import net.corda.ext.api.admin.NodeAdmin
import net.corda.ext.api.attachment.AttachmentOperations
import net.corda.ext.api.flow.StateMachineOperations
import net.corda.ext.api.lifecycle.NodeLifecycleObserver
import net.corda.ext.api.lifecycle.NodeRpcOps
import net.corda.ext.api.network.NetworkMapOperations
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RpcImplFullLifecycleTest {
    companion object {
        const val testResponse = "testResponse"

        @Volatile
        var beforeStartCalled = false

        @Volatile
        var afterStartCalled = false

        @Volatile
        var beforeStopCalled = false

        @Volatile
        var afterStopCalled = false

        class TestNodeHealthCheckRpcOpsImpl : NodeHealthCheckRpcOps, NodeRpcOps<NodeHealthCheckRpcOps> {

            override val protocolVersion: Int = PLATFORM_VERSION

            override val priority: Int = NodeLifecycleObserver.RPC_PRIORITY_NORMAL

            override fun getVersion(nodeServicesContext: NodeInitialContext): Int = 2 // Trumps what `NodeHealthCheckRpcOpsImpl` has

            override val targetInterface: Class<NodeHealthCheckRpcOps> = NodeHealthCheckRpcOps::class.java

            override fun runtimeInfo(): String {
                return testResponse
            }

            override fun update(nodeLifecycleEvent: NodeLifecycleEvent): Try<String> {
                return when (nodeLifecycleEvent) {
                    is NodeLifecycleEvent.BeforeStart -> Try.on {
                        nodeLifecycleEvent.nodeInitialContext.checkOperational()
                        beforeStartCalled = true
                        reportSuccess(nodeLifecycleEvent)
                    }
                    is NodeLifecycleEvent.AfterStart -> Try.on {
                        nodeLifecycleEvent.nodeServicesContext.checkOperational()
                        afterStartCalled = true
                        reportSuccess(nodeLifecycleEvent)
                    }
                    is NodeLifecycleEvent.BeforeStop -> Try.on {
                        nodeLifecycleEvent.nodeServicesContext.checkOperational()
                        beforeStopCalled = true
                        reportSuccess(nodeLifecycleEvent)
                    }
                    is NodeLifecycleEvent.AfterStop -> Try.on {
                        nodeLifecycleEvent.nodeInitialContext.checkOperational()
                        afterStopCalled = true
                        reportSuccess(nodeLifecycleEvent)
                    }
                }
            }
        }

        private fun NodeInitialContext.checkOperational() {
            assertNotNull(platformVersion)
            val extractor = object : Configuration.Value.Extractor<String> {
                override fun valueIn(configuration: Config): String {
                    return configuration.getString("baseDirectory")
                }

                override fun isSpecifiedBy(configuration: Config) = true
            }
            assertNotNull(configurationWithOptions[extractor])
        }

        private fun NodeServicesContext.checkOperational() {
            (this as NodeInitialContext).checkOperational()
            attachmentOperations.checkOperational()
            database.checkOperational()
            // messagingOperations.checkOperational()
            networkMapOperations.checkOperational()
            nodeAdmin.checkOperational()
            serviceHub.checkOperational()
            stateMachineOperations.checkOperational()
        }

        private fun AttachmentOperations.checkOperational() {
            assertNotNull(calculateAllTrustInfo())
        }

        private fun StateMachineOperations.checkOperational() {
            assertNotNull(transactionMappingFeed.track())
            assertFalse(flowHospital.contains(StateMachineRunId(UUID.randomUUID())))
        }

        private fun ServiceHub.checkOperational() {
            assertNotNull(myInfo)
            assertNotNull(networkMapCache.allNodes)
        }

        private fun NodeAdmin.checkOperational() {
            assertNotNull(corDapps)
            assertFalse(propertiesStore.flowsDrainingMode.isEnabled())
        }

        private fun NetworkMapOperations.checkOperational() {
            assertNotNull(trackParametersUpdate())
        }

        private fun CordaTransactionSupport.checkOperational() {
            transaction {
                assertNotNull(session.statistics)
            }
        }
    }

    @Test
    fun `check full lifecycle`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptyList(), notarySpecs = emptyList())) {
            val nodeHandle = startNode(providedName = ALICE_NAME).getOrThrow()
            assertTrue { beforeStartCalled }
            assertTrue { afterStartCalled }
            assertFalse { beforeStopCalled }
            assertFalse { afterStopCalled }

            val rpcUser = nodeHandle.rpcUsers.first()
            RPCClient<NodeHealthCheckRpcOps>(nodeHandle.rpcAddress)
                    .start(NodeHealthCheckRpcOps::class.java, rpcUser.username, rpcUser.password).use {
                        assertEquals(testResponse, it.proxy.runtimeInfo())
                    }
        }

        assertTrue { beforeStopCalled }
        assertTrue { afterStopCalled }
    }
}