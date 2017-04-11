package net.corda.client.rpc

import net.corda.core.messaging.RPCOps
import net.corda.node.services.messaging.requirePermission
import net.corda.nodeapi.PermissionException
import net.corda.nodeapi.User
import org.junit.After
import org.junit.Test
import kotlin.test.assertFailsWith

class RPCPermissionsTest : AbstractClientRPCTest() {
    companion object {
        const val DUMMY_FLOW = "StartFlow.net.corda.flows.DummyFlow"
        const val OTHER_FLOW = "StartFlow.net.corda.flows.OtherFlow"
        const val ALL_ALLOWED = "ALL"
    }

    lateinit var proxy: TestOps

    @After
    fun shutdown() {
        safeClose(proxy)
    }

    /*
     * RPC operation.
     */
    interface TestOps : RPCOps {
        fun validatePermission(str: String)
    }

    class TestOpsImpl : TestOps {
        override val protocolVersion = 1
        override fun validatePermission(str: String) = requirePermission(str)
    }

    /**
     * Create an RPC proxy for the given user.
     */
    private fun proxyFor(rpcUser: User): TestOps = rpcProxyFor(rpcUser, TestOpsImpl(), TestOps::class.java)

    private fun userOf(name: String, permissions: Set<String>) = User(name, "password", permissions)

    @Test
    fun `empty user cannot use any flows`() {
        val emptyUser = userOf("empty", emptySet())
        proxy = proxyFor(emptyUser)
        assertFailsWith(PermissionException::class,
                "User ${emptyUser.username} should not be allowed to use $DUMMY_FLOW.",
                { proxy.validatePermission(DUMMY_FLOW) })
    }

    @Test
    fun `admin user can use any flow`() {
        val adminUser = userOf("admin", setOf(ALL_ALLOWED))
        proxy = proxyFor(adminUser)
        proxy.validatePermission(DUMMY_FLOW)
    }

    @Test
    fun `joe user is allowed to use DummyFlow`() {
        val joeUser = userOf("joe", setOf(DUMMY_FLOW))
        proxy = proxyFor(joeUser)
        proxy.validatePermission(DUMMY_FLOW)
    }

    @Test
    fun `joe user is not allowed to use OtherFlow`() {
        val joeUser = userOf("joe", setOf(DUMMY_FLOW))
        proxy = proxyFor(joeUser)
        assertFailsWith(PermissionException::class,
                "User ${joeUser.username} should not be allowed to use $OTHER_FLOW",
                { proxy.validatePermission(OTHER_FLOW) })
    }

    @Test
    fun `check ALL is implemented the correct way round`() {
        val joeUser = userOf("joe", setOf(DUMMY_FLOW))
        proxy = proxyFor(joeUser)
        assertFailsWith(PermissionException::class,
                "Permission $ALL_ALLOWED should not do anything for User ${joeUser.username}",
                { proxy.validatePermission(ALL_ALLOWED) })
    }

}
