package net.corda.client.rpc

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.messaging.rpcContext
import net.corda.nodeapi.internal.config.User
import net.corda.testing.internal.RPCDriverDSL
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
        fun validatePermission(str: String)
    }

    class TestOpsImpl : TestOps {
        override val protocolVersion = 1
        override fun validatePermission(str: String) { rpcContext().requirePermission(str) }
    }

    /**
     * Create an RPC proxy for the given user.
     */
    private fun RPCDriverDSL.testProxyFor(rpcUser: User) = testProxy<TestOps>(TestOpsImpl(), rpcUser).ops

    private fun userOf(name: String, permissions: Set<String>) = User(name, "password", permissions)

    @Test
    fun `empty user cannot use any flows`() {
        rpcDriver {
            val emptyUser = userOf("empty", emptySet())
            val proxy = testProxyFor(emptyUser)
            assertFailsWith(PermissionException::class,
                    "User ${emptyUser.username} should not be allowed to use $DUMMY_FLOW.",
                    { proxy.validatePermission(DUMMY_FLOW) })
        }
    }

    @Test
    fun `admin user can use any flow`() {
        rpcDriver {
            val adminUser = userOf("admin", setOf(ALL_ALLOWED))
            val proxy = testProxyFor(adminUser)
            proxy.validatePermission(DUMMY_FLOW)
        }
    }

    @Test
    fun `joe user is allowed to use DummyFlow`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            proxy.validatePermission(DUMMY_FLOW)
        }
    }

    @Test
    fun `joe user is not allowed to use OtherFlow`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            assertFailsWith(PermissionException::class,
                    "User ${joeUser.username} should not be allowed to use $OTHER_FLOW",
                    { proxy.validatePermission(OTHER_FLOW) })
        }
    }

    @Test
    fun `check ALL is implemented the correct way round`() {
        rpcDriver {
            val joeUser = userOf("joe", setOf(DUMMY_FLOW))
            val proxy = testProxyFor(joeUser)
            assertFailsWith(PermissionException::class,
                    "Permission $ALL_ALLOWED should not do anything for User ${joeUser.username}",
                    { proxy.validatePermission(ALL_ALLOWED) })
        }
    }

    @Test
    fun `fine grained permissions are enforced`() {
        val allPermissions = CordaRPCOps::class.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }.map { invokeRpc(it) }
        allPermissions.forEach { permission ->
            rpcDriver {
                val user = userOf("Mark", setOf(permission))
                val proxy = testProxyFor(user)

                proxy.validatePermission(permission)
                (allPermissions - permission).forEach { notOwnedPermission ->
                    assertFailsWith(PermissionException::class, { proxy.validatePermission(notOwnedPermission) })
                }
            }
        }
    }
}
