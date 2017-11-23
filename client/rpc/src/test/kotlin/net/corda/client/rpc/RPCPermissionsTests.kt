package net.corda.client.rpc

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.messaging.rpcContext
import net.corda.nodeapi.User
import net.corda.testing.internal.RPCDriverExposedDSLInterface
import net.corda.testing.internal.rpcDriver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
class RPCPermissionsTests : AbstractRPCTest() {
    companion object {
        const val DUMMY_FLOW = "StartFlow.net.corda.flows.DummyFlow"
        const val OTHER_FLOW = "StartFlow.net.corda.flows.OtherFlow"
        const val ALL_ALLOWED = "ALL"
    }

    /*
     * RPC operation.
     */
    interface TestOps : RPCOps {
        fun validatePermission(method : String, target : String? = null)
    }

    class TestOpsImpl : TestOps {
        override val protocolVersion = 1
        override fun validatePermission(method : String, target : String?) {
            val authorized = if (target == null) {
                rpcContext().authorizer.isPermitted(method)
            } else {
                rpcContext().authorizer.isPermitted(method, target)
            }
            if (!authorized) {
                throw PermissionException("RPC user not authorized")
            }
        }
    }

    /**
     * Create an RPC proxy for the given user.
     */
    private fun RPCDriverExposedDSLInterface.testProxyFor(rpcUser: User) = testProxy<TestOps>(TestOpsImpl(), rpcUser).ops

    private fun userOf(name: String, permissions: Set<String>) = User(name, "password", permissions)

    @Test
    fun `empty user cannot use any flows`() {
        rpcDriver {
            val emptyUser = userOf("empty", emptySet())
            val proxy = testProxyFor(emptyUser)
            assertFailsWith(PermissionException::class,
                    "User ${emptyUser.username} should not be allowed to use $DUMMY_FLOW.",
                    { proxy.validatePermission("startFlowDynamic", "net.corda.flows.DummyFlow") })
        }
    }

    @Test
    fun `admin user can use any flow`() {
        rpcDriver {
            val adminUser = userOf("admin", setOf(ALL_ALLOWED))
            val proxy = testProxyFor(adminUser)
            proxy.validatePermission("startFlowDynamic", "net.corda.flows.DummyFlow")
            proxy.validatePermission("startTrackedFlowDynamic", "net.corda.flows.DummyFlow")
        }
    }

    @Test
    fun `joe user is allowed to use DummyFlow`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            proxy.validatePermission("startFlowDynamic", "net.corda.flows.DummyFlow")
            proxy.validatePermission("startTrackedFlowDynamic", "net.corda.flows.DummyFlow")
        }
    }

    @Test
    fun `joe user is not allowed to use OtherFlow`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            assertFailsWith(PermissionException::class,
                    "User ${joeUser.username} should not be allowed to use $OTHER_FLOW",
                    {
                        proxy.validatePermission("startFlowDynamic", "net.corda.flows.OtherFlow")
                        proxy.validatePermission("startTrackedFlowDynamic", "net.corda.flows.OtherFlow")
                    })
        }
    }

    @Test
    fun `joe user is not allowed to call other RPC methods`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            assertFailsWith(PermissionException::class,
                    "User ${joeUser.username} should not be allowed to invoke RPC other than for starting flows",
                    {
                      proxy.validatePermission("nodeInfo")
                      proxy.validatePermission("networkMapFeed")
                    })
        }
    }

    @Test
    fun `checking invokeRpc permissions entitlements`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf("InvokeRpc.networkMapFeed"))
            val proxy = testProxyFor(joeUser)
            assertFailsWith(PermissionException::class,
                    "User ${joeUser.username} should not be allowed to invoke RPC other than for starting flows",
                    {
                        proxy.validatePermission("nodeInfo")
                        proxy.validatePermission("startTrackedFlowDynamic", "net.corda.flows.OtherFlow")
                    })
            proxy.validatePermission("networkMapFeed")
        }
    }

}
