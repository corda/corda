package net.corda.node.services

import net.corda.core.context.AuthServiceId
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.internal.security.Password
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.internal.security.tryAuthenticate
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.config.SecurityConfiguration
import net.corda.nodeapi.internal.config.User
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import javax.security.auth.login.FailedLoginException
import kotlin.reflect.KFunction
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RPCSecurityManagerTest {

    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }

    @Test
    fun `Generic RPC call authorization`() {
        checkUserActions(
                permitted = setOf(arrayListOf("nodeInfo"), arrayListOf("notaryIdentities")),
                permissions = setOf(
                        invokeRpc(CordaRPCOps::nodeInfo),
                        invokeRpc(CordaRPCOps::notaryIdentities)))
    }

    @Test
    fun `Flow invocation authorization`() {
        checkUserActions(
            permissions = setOf(Permissions.startFlow<DummyFlow>()),
            permitted = setOf(
                arrayListOf("startTrackedFlowDynamic", "net.corda.node.services.RPCSecurityManagerTest\$DummyFlow"),
                arrayListOf("startFlowDynamic", "net.corda.node.services.RPCSecurityManagerTest\$DummyFlow")))
    }

    @Test
    fun `Check startFlow RPC permission implies startFlowDynamic`() {
        checkUserActions(
                permissions = setOf(invokeRpc("startFlow")),
                permitted = setOf(arrayListOf("startFlow"), arrayListOf("startFlowDynamic")))
    }

    @Test
    fun `Check startTrackedFlow RPC permission implies startTrackedFlowDynamic`() {
        checkUserActions(
                permitted = setOf(arrayListOf("startTrackedFlow"), arrayListOf("startTrackedFlowDynamic")),
                permissions = setOf(invokeRpc("startTrackedFlow")))
    }

    @Test
    fun `Admin authorization`() {
        checkUserActions(
            permissions = setOf("all"),
            permitted = allActions.map { arrayListOf(it) }.toSet())
    }

    @Test
    fun `flows draining mode permissions`() {
        checkUserActions(
                permitted = setOf(arrayListOf("setFlowsDrainingModeEnabled")),
                permissions = setOf(invokeRpc(CordaRPCOps::setFlowsDrainingModeEnabled))
        )
        checkUserActions(
                permitted = setOf(arrayListOf("isFlowsDrainingModeEnabled")),
                permissions = setOf(invokeRpc(CordaRPCOps::isFlowsDrainingModeEnabled))
        )
    }

    @Test
    fun `Malformed permission strings`() {
        assertMalformedPermission("bar")
        assertMalformedPermission("InvokeRpc.nodeInfo.XXX")
        assertMalformedPermission("")
        assertMalformedPermission(".")
        assertMalformedPermission("..")
        assertMalformedPermission("startFlow")
        assertMalformedPermission("startFlow.")
    }

    @Test
    fun `Login with unknown user`() {
        val userRealm = RPCSecurityManagerImpl.fromUserList(
                users = listOf(User("user", "xxxx", emptySet())),
                id = AuthServiceId("TEST"))
        userRealm.authenticate("user", Password("xxxx"))
        assertFailsWith(FailedLoginException::class, "Login with wrong password should fail") {
            userRealm.authenticate("foo", Password("xxxx"))
        }
        assertNull(userRealm.tryAuthenticate("foo", Password("wrong")),
                "Login with wrong password should fail")
    }

    @Test
    fun `Login with wrong credentials`() {
        val userRealm = RPCSecurityManagerImpl.fromUserList(
                users = listOf(User("user", "password", emptySet())),
                id = AuthServiceId("TEST"))
        userRealm.authenticate("user", Password("password"))
        assertFailsWith(FailedLoginException::class, "Login with wrong password should fail") {
            userRealm.authenticate("user", Password("wrong"))
        }
        assertNull(userRealm.tryAuthenticate("user", Password("wrong")),
                "Login with wrong password should fail")
    }

    @Test
    fun `Build invalid subject`() {
        val userRealm = RPCSecurityManagerImpl.fromUserList(
                users = listOf(User("user", "password", emptySet())),
                id = AuthServiceId("TEST"))
        val subject = userRealm.buildSubject("foo")
        for (action in allActions) {
            require(!subject.isPermitted(action)) {
                "Invalid subject should not be allowed to call $action"
            }
        }
    }

    private fun configWithRPCUsername(username: String) {
        RPCSecurityManagerImpl.fromUserList(
                users = listOf(User(username, "password", setOf())), id = AuthServiceId("TEST"))
    }

    private fun checkUserActions(permissions: Set<String>, permitted: Set<ArrayList<String>>) {
        val user = User(username = "user", password = "password", permissions = permissions)
        val userRealms = RPCSecurityManagerImpl(SecurityConfiguration.AuthService.fromUsers(listOf(user)))
        val disabled = allActions.filter { !permitted.contains(listOf(it)) }
        for (subject in listOf(
                userRealms.authenticate("user", Password("password")),
                userRealms.tryAuthenticate("user", Password("password"))!!,
                userRealms.buildSubject("user"))) {
            for (request in permitted) {
                val call = request.first()
                val args = request.drop(1).toTypedArray()
                require(subject.isPermitted(request.first(), *args)) {
                    "User ${subject.principal} should be permitted $call with target '${request.toList()}'"
                }
                if (args.isEmpty()) {
                    require(subject.isPermitted(request.first(), "XXX")) {
                        "User ${subject.principal} should be permitted $call with any target"
                    }
                }
            }

            disabled.forEach {
                require(!subject.isPermitted(it)) {
                    "Permissions $permissions should not allow to call $it"
                }
            }

            disabled.filter { !permitted.contains(listOf(it, "foo")) }.forEach {
                require(!subject.isPermitted(it, "foo")) {
                    "Permissions $permissions should not allow to call $it with argument 'foo'"
                }
            }
        }
    }

    private fun assertMalformedPermission(permission: String) {
        assertFails {
            RPCSecurityManagerImpl.fromUserList(
                    users = listOf(User("x", "x", setOf(permission))),
                    id = AuthServiceId("TEST"))
        }
    }

    companion object {
        private val allActions = CordaRPCOps::class.members.filterIsInstance<KFunction<*>>().map { it.name }.toSet() +
                setOf("startFlow", "startTrackedFlow")
    }

    private abstract class DummyFlow : FlowLogic<Unit>()
}