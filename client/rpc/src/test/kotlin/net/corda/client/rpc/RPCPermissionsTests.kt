package net.corda.client.rpc

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.RPCOps
import net.corda.core.messaging.startFlow
import net.corda.node.services.messaging.rpcContext
import net.corda.node.services.security.RPCPermission
import net.corda.nodeapi.User
import net.corda.testing.RPCDriverExposedDSLInterface
import net.corda.testing.rpcDriver
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertEquals

internal class DummyFlow {}
internal class OtherFlow {}

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
        override fun validatePermission(str: String) {
            val permission = RPCPermission(str)
            val userPermissions = rpcContext().currentUser.permissions.map {RPCPermission(it)}
            if (!userPermissions.any {it.implies(permission)}) {
                throw PermissionException ("Current user permissions do not authorise $permission")
            }
        }
    }

    /**
     * Create an RPC proxy for the given user.
     */
    private fun RPCDriverExposedDSLInterface.testProxyFor(rpcUser: User) = testProxy<TestOps>(TestOpsImpl(), rpcUser).ops

    private fun userOf(name: String, permissions: Set<String>) = User(name, "password", permissions)

    @Test
    fun `test RPCPermission construction`() {
        assertEquals(RPCPermission("ALL"),  RPCPermission.all)
        assertEquals(RPCPermission("InvokeRpc.nodeInfo"), RPCPermission.invokeRpc(CordaRPCOps::nodeInfo))
        assertEquals(RPCPermission("InvokeRpc.vaultTrackBy"), RPCPermission.invokeRpc("vaultTrackBy"))
        assertEquals(RPCPermission("startflow.net.corda.client.rpc.DummyFlow"),
                RPCPermission.startFlow("net.corda.client.rpc.DummyFlow"))
        assertFailsWith (IllegalArgumentException::class,
                "Parsing of malformed permission string should raise IllegalArgumentException") {
            RPCPermission("")
            RPCPermission("foo")
            RPCPermission("all") //< case un-sensitivity
            RPCPermission("InvokeRpc")
            RPCPermission("InvokeRpc.")
            RPCPermission("StartFlow")
            RPCPermission("StartFlow.")
            RPCPermission("StartFlow.*") //< No wildcards
            RPCPermission("InvokeRpc.X.Y")
            RPCPermission.invokeRpc("abcdefg") //< function not exposed in CordaRPCOps
        }
        assertEquals(RPCPermission("ALL").toConfigString(), "ALL")
        assertEquals(RPCPermission.invokeRpc("nodeInfo").toConfigString(), "InvokeRpc.nodeInfo")
        assertEquals(RPCPermission.startFlow("x.y.foo").toConfigString(), "StartFlow.x.y.foo")
    }

    @Test
    fun `test RPCPermission entitlements`() {

        val startFlowPerm1 = RPCPermission("StartFlow.net.corda.client.rpc.DummyFlow")
        val startFlowPerm2 = RPCPermission("StartFlow.net.corda.client.rpc.OtherFlow")
        val rpcPerm1 = RPCPermission("InvokeRpc.nodeInfo")
        val rpcPerm2 = RPCPermission("InvokeRpc.vaultTrackBy")
        val rpcPerm3 = RPCPermission.invokeRpc("startFlowDynamic")

        assertTrue("ALL should grant every permission") {
            RPCPermission.all.implies(startFlowPerm1) &&
                    RPCPermission.all.implies(startFlowPerm2) &&
                    RPCPermission.all.implies(rpcPerm1) &&
                    RPCPermission.all.implies(rpcPerm2) &&
                    RPCPermission.all.implies(RPCPermission("ALL"))
        }
        assertTrue("StartFlow and InvokeRpc cannot grant ALL permission") {
            !startFlowPerm1.implies(RPCPermission.all) &&
            !startFlowPerm2.implies(RPCPermission.all) &&
            !rpcPerm1.implies(RPCPermission("ALL")) &&
            !rpcPerm2.implies(RPCPermission.all)
        }
        assertTrue("StartFlow permission must imply permissions to start target flow via any of the flow RPC calls") {
            startFlowPerm1.implies(RPCPermission.invokeRpc("startFlowDynamic").onTarget<DummyFlow>()) &&
                    startFlowPerm1.implies(RPCPermission.invokeRpc("startTrackedFlowDynamic").onTarget<DummyFlow>())
        }
        assertTrue("Permission to RPC a function should allow to call it on any target") {
            rpcPerm3.implies(RPCPermission.invokeRpc("startFlowDynamic").onTarget<DummyFlow>()) &&
                    rpcPerm3.implies(RPCPermission.invokeRpc("startFlowDynamic").onTarget<OtherFlow>())
        }
        assertTrue("Permission to start a specific flow via RPC should not entail permission to start a generic flow") {
            !RPCPermission.invokeRpc("startFlowDynamic").onTarget<DummyFlow>().implies(rpcPerm3) &&
                    !RPCPermission.invokeRpc("startFlowDynamic").onTarget<OtherFlow>().implies(rpcPerm3)
        }
        assertTrue("Permission $startFlowPerm1 cannot imply $rpcPerm1") {
            !startFlowPerm1.implies(rpcPerm1)
        }
    }

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
        val allPermissions = CordaRPCOps::class.declaredMemberFunctions
                .filter { it.visibility == KVisibility.PUBLIC }
                .map { RPCPermission.invokeRpc(it).toConfigString() }

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
