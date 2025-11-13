package net.corda.node.services

import net.corda.core.context.AuthServiceId
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.internal.rpc.proxies.RpcAuthHelper
import net.corda.node.internal.security.Password
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.internal.security.tryAuthenticate
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.node.services.config.AuthDataSourceType
import net.corda.node.services.config.PasswordEncryption
import net.corda.node.services.config.SecurityConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.fromUserList
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import javax.security.auth.login.FailedLoginException
import kotlin.reflect.KFunction
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RPCSecurityManagerTest {

    @Test(timeout=300_000)
	fun `Artemis special characters not permitted in RPC usernames`() {
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }

    @Test(timeout=300_000)
	fun `Generic RPC call authorization`() {
        checkUserActions(
                permitted = setOf(listOf("nodeInfo"), listOf("notaryIdentities")),
                permissions = setOf(
                        invokeRpc(CordaRPCOps::nodeInfo),
                        invokeRpc(CordaRPCOps::notaryIdentities)))
    }

    @Test(timeout=300_000)
	fun `Flow invocation authorization`() {
        checkUserActions(
            permissions = setOf(startFlow<DummyFlow>()),
            permitted = setOf(
                listOf("startTrackedFlowDynamic", DummyFlow::class.java.name),
                listOf("startFlowDynamic", DummyFlow::class.java.name)))
    }

    @Test(timeout=300_000)
	fun `Check startFlow RPC permission implies startFlowDynamic`() {
        checkUserActions(
                permissions = setOf(invokeRpc("startFlow")),
                permitted = setOf(listOf("startFlow"), listOf("startFlowDynamic")))
    }

    @Test(timeout=300_000)
	fun `Check startTrackedFlow RPC permission implies startTrackedFlowDynamic`() {
        checkUserActions(
                permitted = setOf(listOf("startTrackedFlow"), listOf("startTrackedFlowDynamic")),
                permissions = setOf(invokeRpc("startTrackedFlow")))
    }

    @Test(timeout=300_000)
	fun `check killFlow RPC permission accepted`() {
        checkUserActions(
                permitted = setOf(listOf("killFlow")),
                permissions = setOf(invokeRpc(CordaRPCOps::killFlow))
        )
    }

    @Test(timeout=300_000)
	fun `Admin authorization`() {
        checkUserActions(
            permissions = setOf("all"),
            permitted = allActions.map { listOf(it) }.toSet())
    }

    @Test(timeout=300_000)
	fun `flows draining mode permissions`() {
        checkUserActions(
                permitted = setOf(listOf("setFlowsDrainingModeEnabled")),
                permissions = setOf(invokeRpc(CordaRPCOps::setFlowsDrainingModeEnabled))
        )
        checkUserActions(
                permitted = setOf(listOf("isFlowsDrainingModeEnabled")),
                permissions = setOf(invokeRpc(CordaRPCOps::isFlowsDrainingModeEnabled))
        )
    }

    @Test(timeout=300_000)
	fun `Malformed permission strings`() {
        assertMalformedPermission("bar")
        assertMalformedPermission("InvokeRpc.nodeInfo.XXX")
        assertMalformedPermission("")
        assertMalformedPermission(".")
        assertMalformedPermission("..")
        assertMalformedPermission("startFlow")
        assertMalformedPermission("startFlow.")
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout = 300_000)
    fun `Exponential backoff triggers after consecutive failed logins`() {
        val users = listOf(User("user", "password", emptySet()))
        val userRealm = RPCSecurityManagerImpl(
                config = SecurityConfiguration.AuthService(
                        options = SecurityConfiguration.AuthService.Options(
                                cache = null,
                                rateLimit = SecurityConfiguration.AuthService.Options.RateLimit(2L, 60L, 15)
                        ),
                        dataSource = SecurityConfiguration.AuthService.DataSource(
                                type = AuthDataSourceType.INMEMORY,
                                users = users,
                                passwordEncryption = PasswordEncryption.NONE),
                        id = AuthServiceId("NODE_CONFIG")
                ),
                cacheFactory = TestingNamedCacheFactory()
        )

        // 1st failure — should fail immediately
        val login1 = assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("wrong"))
        }
        require(login1.message!!.contains("Failed login for user 'user'. Try again in 2 seconds.")) {
            "Expected exponential backoff lockout message, got: ${login1.message}"
        }

        // 2nd failure — should increase delay
        val login2 = assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("wrong"))
        }
        require(login2.message!!.contains("Failed login for user 'user'. Try again in 4 seconds.")) {
            "Expected exponential backoff lockout message, got: ${login2.message}"
        }

        val login3 = assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("password"))
        }
        require(login3.message!!.contains("Failed login for user 'user'. Try again in 8 seconds.")) {
            "Expected exponential backoff lockout message, got: ${login3.message}"
        }

        val login4 = assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("password"))
        }
        require(login4.message!!.contains("Failed login for user 'user'. Try again in 16 seconds.")) {
            "Expected exponential backoff lockout message, got: ${login4.message}"
        }

        val login5 = assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("password"))
        }
        require(login5.message!!.contains("Failed login for user 'user'. Try again in 32 seconds.")) {
            "Expected exponential backoff lockout message, got: ${login5.message}"
        }

        val login6 = assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("password"))
        }
        require(login6.message!!.contains("Failed login for user 'user'. Try again in 60 seconds.")) {
            "Expected exponential backoff lockout message, got: ${login6.message}"
        }

        // Another login - backoff should still be at 60 seconds
        val login7 = assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("password"))
        }
        require(login7.message!!.contains("Failed login for user 'user'. Try again in 60 seconds.")) {
            "Expected exponential backoff lockout message, got: ${login7.message}"
        }
    }

    @Test(timeout = 300_000)
    fun `Backoff window expires after delay allowing login again`() {
        val users = listOf(User("user", "password", emptySet()))
        val userRealm = RPCSecurityManagerImpl(
                config = SecurityConfiguration.AuthService(
                        options = SecurityConfiguration.AuthService.Options(
                                cache = null,
                                rateLimit = SecurityConfiguration.AuthService.Options.RateLimit(2L, 60L, 15)
                        ),
                        dataSource = SecurityConfiguration.AuthService.DataSource(
                                type = AuthDataSourceType.INMEMORY,
                                users = users,
                                passwordEncryption = PasswordEncryption.NONE),
                        id = AuthServiceId("NODE_CONFIG")
                ),
                cacheFactory = TestingNamedCacheFactory()
        )

        // Fail once to trigger 2s delay
        assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("wrong"))
        }

        // Immediately retry — should still be locked and increase delay to 4s
        assertFailsWith(FailedLoginException::class) {
            userRealm.authenticate("user", Password("password"))
        }

        // Wait for the backoff period (~4s)
        Thread.sleep(4100)

        // Now should succeed
        userRealm.authenticate("user", Password("password"))
    }

    private fun configWithRPCUsername(username: String) {
        RPCSecurityManagerImpl.fromUserList(
                users = listOf(User(username, "password", setOf())), id = AuthServiceId("TEST"))
    }

    private fun checkUserActions(permissions: Set<String>, permitted: Set<List<String>>) {
        val user = User(username = "user", password = "password", permissions = permissions)
        val userRealms = RPCSecurityManagerImpl(SecurityConfiguration.AuthService.fromUsers(listOf(user)), TestingNamedCacheFactory())
        val disabled = allActions.filter { !permitted.contains(listOf(it)) }
        for (subject in listOf(
                userRealms.authenticate("user", Password("password")),
                userRealms.tryAuthenticate("user", Password("password"))!!,
                userRealms.buildSubject("user"))) {
            for (request in permitted) {
                val methodName = RpcAuthHelper.methodFullName(CordaRPCOps::class.java, request.first())
                val args = request.drop(1).toTypedArray()
                require(subject.isPermitted(methodName, *args)) {
                    "User ${subject.principal} should be permitted $methodName with target '${request.toList()}'"
                }
                if (args.isEmpty()) {
                    require(subject.isPermitted(methodName, "XXX")) {
                        "User ${subject.principal} should be permitted $methodName with any target"
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