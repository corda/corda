package net.corda.node.services

import net.corda.core.context.AuthServiceId
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.internal.security.AuthorizingSubject
import net.corda.node.internal.security.Password
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.internal.security.tryAuthenticate
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
        val rpcUser = User(
                username = "rpcUser",
                password = "password",
                permissions = setOf(
                        Permissions.invokeRpc(CordaRPCOps::nodeInfo),
                        Permissions.invokeRpc(CordaRPCOps::notaryIdentities)))
        val userRealms = RPCSecurityManagerImpl.fromUserList(users = listOf(rpcUser),id = AuthServiceId("TEST"))
        val permitted = setOf(arrayListOf("nodeInfo"), arrayListOf("notaryIdentities"))
        val subjects = listOf(
                userRealms.authenticate("rpcUser", Password("password")),
                userRealms.tryAuthenticate("rpcUser", Password("password"))!!,
                userRealms.buildSubject("rpcUser"))
        for (subject in subjects) {
            checkUserPermissions(subject, permitted)
        }
    }

    @Test
    fun `Flow invocation authorization`() {
        val flowUser = User(
            username = "flowUser",
            password = "password2",
            permissions = setOf(Permissions.startFlow<DummyFlow>()))
        val userRealms = RPCSecurityManagerImpl.fromUserList(users = listOf(flowUser),id = AuthServiceId("TEST"))
        val permitted = setOf(
                arrayListOf("startTrackedFlowDynamic", "net.corda.node.services.RPCSecurityManagerTest\$DummyFlow"),
                arrayListOf("startFlowDynamic", "net.corda.node.services.RPCSecurityManagerTest\$DummyFlow"))
        val subjects = listOf(
                userRealms.authenticate("flowUser", Password("password2")),
                userRealms.tryAuthenticate("flowUser", Password("password2"))!!,
                userRealms.buildSubject("flowUser"))
        for (subject in subjects) {
            checkUserPermissions(subject, permitted)
        }
    }

    @Test
    fun `Admin authorization`() {
        val userRealms = RPCSecurityManagerImpl.fromUserList(
                users = listOf(User(username = "admin", password = "admin", permissions = setOf("all"))),
                id = AuthServiceId("TEST"))
        val permitted = allActions.map { arrayListOf(it) }.toSet()
        val subjects = listOf(
                userRealms.authenticate("admin", Password("admin")),
                userRealms.tryAuthenticate("admin", Password("admin"))!!,
                userRealms.buildSubject("admin"))
        for (subject in subjects) {
            checkUserPermissions(subject, permitted)
        }
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
        checkUserPermissions(subject, emptySet())
    }

    private fun configWithRPCUsername(username: String) {
        RPCSecurityManagerImpl.fromUserList(
                users = listOf(User(username, "password", setOf())), id = AuthServiceId("TEST"))
    }

    private fun checkUserPermissions(subject: AuthorizingSubject, permitted: Set<ArrayList<String>>) {
        for (request in permitted) {
            val call = request.first()
            val args = request.drop(1).toTypedArray()
            assert(subject.isPermitted(request.first(), *args)) {
                "User ${subject.principal} should be permitted ${call} with target '${request.toList()}'"
            }
        }

        val disabled = allActions.filter {!permitted.contains(listOf(it))}
        disabled.forEach {
            assert(!subject.isPermitted(it)) {
                "User ${subject.principal} should not be permitted $it"
            }
        }

        disabled.filter {!permitted.contains(listOf(it, "foo"))}.forEach {
            assert(!subject.isPermitted(it, "foo")) {
                "User ${subject.principal} should not be permitted $it with target 'foo'"
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
        private val allActions = CordaRPCOps::class.members.filterIsInstance<KFunction<*>>().map { it.name }.toSet()
    }

    private abstract class DummyFlow : FlowLogic<Unit>()
}