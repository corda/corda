package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.PermissionException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.PasswordEncryption
import net.corda.node.services.config.SecurityConfiguration
import net.corda.node.services.config.AuthDataSourceType
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.toConfig
import net.corda.testing.node.internal.NodeBasedTest
import net.corda.testing.*
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.DriverManager
import java.sql.Statement
import java.util.*
import kotlin.test.assertFailsWith

abstract class UserAuthServiceTest : NodeBasedTest() {

    protected lateinit var node: StartedNode<Node>
    protected lateinit var client: CordaRPCClient

    @Test
    fun `login with correct credentials`() {
        client.start("user", "foo")
    }

    @Test
    fun `login with wrong credentials`() {
        client.start("user", "foo")
        assertFailsWith(
                ActiveMQSecurityException::class,
                "Login with incorrect password should fail") {
            client.start("user", "bar")
        }
        assertFailsWith(
                ActiveMQSecurityException::class,
                "Login with unknown username should fail") {
            client.start("X", "foo")
        }
    }

    @Test
    fun `check flow permissions are respected`() {
        client.start("user", "foo").use {
            val proxy = it.proxy
            proxy.startFlowDynamic(DummyFlow::class.java)
            proxy.startTrackedFlowDynamic(DummyFlow::class.java)
            proxy.startFlow(::DummyFlow)
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to start flow `CashIssueFlow`") {
                proxy.startFlowDynamic(CashIssueFlow::class.java)
            }
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to start flow `CashIssueFlow`") {
                proxy.startTrackedFlowDynamic(CashIssueFlow::class.java)
            }
        }
    }

    @Test
    fun `check permissions on RPC calls are respected`() {
        client.start("user", "foo").use {
            val proxy = it.proxy
            proxy.stateMachinesFeed()
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to call 'nodeInfo'") {
                proxy.nodeInfo()
            }
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class DummyFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = Unit
    }
}

class UserAuthServiceEmbedded : UserAuthServiceTest() {

    private val rpcUser = User("user", "foo", permissions = setOf(
            Permissions.startFlow<DummyFlow>(),
            Permissions.invokeRpc("vaultQueryBy"),
            Permissions.invokeRpc(CordaRPCOps::stateMachinesFeed),
            Permissions.invokeRpc("vaultQueryByCriteria")))

    @Before
    fun setup() {
        val securityConfig = SecurityConfiguration(
               authService = SecurityConfiguration.AuthService.fromUsers(listOf(rpcUser)))

        val configOverrides = mapOf("security" to securityConfig.toConfig().root().unwrapped())
        node = startNode(ALICE_NAME, rpcUsers = emptyList(), configOverrides = configOverrides)
        client = CordaRPCClient(node.internals.configuration.rpcAddress!!)
    }
}

class UserAuthServiceTestsJDBC : UserAuthServiceTest() {

    private val db = UsersDB(
            name = "SecurityDataSourceTestDB",
            users = listOf(UserAndRoles(username = "user",
                    password = "foo",
                    roles = listOf("default"))),
            roleAndPermissions = listOf(
                    RoleAndPermissions(
                            role = "default",
                            permissions = listOf(
                                    Permissions.startFlow<DummyFlow>(),
                                    Permissions.invokeRpc("vaultQueryBy"),
                                    Permissions.invokeRpc(CordaRPCOps::stateMachinesFeed),
                                    Permissions.invokeRpc("vaultQueryByCriteria"))),
                    RoleAndPermissions(
                            role = "admin",
                            permissions = listOf("ALL")
                    )))

    @Before
    fun setup() {
        val securityConfig = SecurityConfiguration(
                authService = SecurityConfiguration.AuthService(
                        dataSource = SecurityConfiguration.AuthService.DataSource(
                                type = AuthDataSourceType.DB,
                                passwordEncryption = PasswordEncryption.NONE,
                                connection = Properties().apply {
                                    setProperty("jdbcUrl", db.jdbcUrl)
                                    setProperty("username", "")
                                    setProperty("password", "")
                                    setProperty("driverClassName", "org.h2.Driver")
                                }
                        )
                )
        )

        val configOverrides = mapOf("security" to securityConfig.toConfig().root().unwrapped())
        node = startNode(ALICE_NAME, rpcUsers = emptyList(), configOverrides = configOverrides)
        client = CordaRPCClient(node.internals.configuration.rpcAddress!!)
    }

    @Test
    fun `Add new users on-the-fly`() {
        assertFailsWith(
                ActiveMQSecurityException::class,
                "Login with incorrect password should fail") {
            client.start("user2", "bar")
        }

        db.insert(UserAndRoles(
                username = "user2",
                password = "bar",
                roles = listOf("default")))

        client.start("user2", "bar")
    }

    @Test
    fun `Modify user permissions during RPC session`() {
        db.insert(UserAndRoles(
                username = "user3",
                password = "bar",
                roles = emptyList()))


        client.start("user3", "bar").use {
            val proxy = it.proxy
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to call 'nodeInfo'") {
                proxy.stateMachinesFeed()
            }
            db.addRoleToUser("user3", "default")
            proxy.stateMachinesFeed()
        }
    }

    @Test
    fun `Revoke user permissions during RPC session`() {
        db.insert(UserAndRoles(
                username = "user4",
                password = "test",
                roles = listOf("default")))

        client.start("user4", "test").use {
            val proxy = it.proxy
            proxy.stateMachinesFeed()
            db.deleteUser("user4")
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to call 'nodeInfo'") {
                proxy.stateMachinesFeed()
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }
}

private data class UserAndRoles(val username: String, val password: String, val roles: List<String>)
private data class RoleAndPermissions(val role: String, val permissions: List<String>)

private class UsersDB : AutoCloseable {

    val jdbcUrl: String

    companion object {
        val DB_CREATE_SCHEMA = """
            CREATE TABLE users (username VARCHAR(256), password TEXT);
            CREATE TABLE user_roles (username VARCHAR(256), role_name VARCHAR(256));
            CREATE TABLE roles_permissions (role_name VARCHAR(256), permission TEXT);
            """
    }

    fun insert(user: UserAndRoles) {
        session {
            it.execute("INSERT INTO users VALUES ('${user.username}', '${user.password}')")
            for (role in user.roles) {
                it.execute("INSERT INTO user_roles VALUES ('${user.username}', '${role}')")
            }
        }
    }

    fun insert(roleAndPermissions: RoleAndPermissions) {
        val (role, permissions) = roleAndPermissions
        session {
            for (permission in permissions) {
                it.execute("INSERT INTO roles_permissions VALUES ('$role', '$permission')")
            }
        }
    }

    fun addRoleToUser(username: String, role: String) {
        session {
            it.execute("INSERT INTO user_roles VALUES ('$username', '$role')")
        }
    }

    fun deleteRole(role: String) {
        session {
            it.execute("DELETE FROM role_permissions WHERE role_name = '$role'")
        }
    }

    fun deleteUser(username: String) {
        session {
            it.execute("DELETE FROM users WHERE username = '$username'")
            it.execute("DELETE FROM user_roles WHERE username = '$username'")
        }
    }

    inline private fun session(statement: (Statement) -> Unit) {
        DriverManager.getConnection(jdbcUrl).use {
            it.autoCommit = false
            it.createStatement().use(statement)
            it.commit()
        }
    }

    constructor(name: String,
                users: List<UserAndRoles> = emptyList(),
                roleAndPermissions: List<RoleAndPermissions> = emptyList()) {

        jdbcUrl = "jdbc:h2:mem:${name};DB_CLOSE_DELAY=-1"

        session {
            it.execute(DB_CREATE_SCHEMA)
        }

        require(users.map { it.username }.toSet().size == users.size) {
            "Duplicate username in input"
        }

        users.forEach { insert(it) }
        roleAndPermissions.forEach { insert(it) }
    }

    override fun close() {
        DriverManager.getConnection(jdbcUrl).use {
            it.createStatement().use {
                it.execute("DROP ALL OBJECTS")
            }
        }
    }
}