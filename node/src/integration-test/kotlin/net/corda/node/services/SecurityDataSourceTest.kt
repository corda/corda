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
import net.corda.node.services.config.SecurityDataSourceConfig
import net.corda.node.services.config.SecurityDataSourceType
import net.corda.nodeapi.User
import net.corda.nodeapi.config.toConfig
import net.corda.testing.internal.NodeBasedTest
import net.corda.testing.*
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.DriverManager
import java.sql.Statement
import java.util.*
import kotlin.test.assertFailsWith

abstract class SecurityDataSourceTest : NodeBasedTest () {

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
            assertFailsWith (
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

class SecurityDataSourceTestEmbedded : SecurityDataSourceTest() {

    private val rpcUser = User("user", "foo", permissions = setOf(
            Permissions.startFlow<DummyFlow>(),
            Permissions.invokeRpc("vaultQueryBy"),
            Permissions.invokeRpc(CordaRPCOps::stateMachinesFeed),
            Permissions.invokeRpc("vaultQueryByCriteria")))

    @Before
    fun setup() {
        val dataSourceConfig = SecurityDataSourceConfig(
                type = SecurityDataSourceType.EMBEDDED,
                passwordEncryption = PasswordEncryption.NONE,
                users = listOf(rpcUser))
                    .toConfig().root().unwrapped()

        val configOverrides = mapOf("securityDataSource" to dataSourceConfig)
        node = startNode(ALICE_NAME, rpcUsers = emptyList(), configOverrides = configOverrides)
        client = CordaRPCClient(node.internals.configuration.rpcAddress!!)
    }
}

class SecurityDataSourceTestJDBC : SecurityDataSourceTest() {

    private val db = MockUsersDB(
            name = "SecurityDataSourceTestDB",
            users = listOf(UserAndRoles(username = "user",
                    password = "foo",
                    roles = listOf("default"))),
            roleAndPermissions = listOf(
                    RoleAndPermission(
                            role = "default",
                            permissions = listOf(
                                    Permissions.startFlow<DummyFlow>(),
                                    Permissions.invokeRpc("vaultQueryBy"),
                                    Permissions.invokeRpc(CordaRPCOps::stateMachinesFeed),
                                    Permissions.invokeRpc("vaultQueryByCriteria"))),
                    RoleAndPermission(
                            role = "admin",
                            permissions = listOf("ALL")
                    )))
    @Before
    fun setup() {

        val dataSourceConfig = SecurityDataSourceConfig(
                type = SecurityDataSourceType.JDBC,
                passwordEncryption = PasswordEncryption.NONE,
                dataSourceProperties = Properties().apply {
                    setProperty("jdbcUrl", db.jdbcUrl)
                    setProperty("username", "")
                    setProperty("password", "")
                    setProperty("driverClassName", "org.h2.Driver")
                }).toConfig().root().unwrapped()

        val configOverrides = mapOf("securityDataSource" to dataSourceConfig)
        node = startNode(ALICE_NAME, rpcUsers = emptyList(), configOverrides = configOverrides)
        client = CordaRPCClient(node.internals.configuration.rpcAddress!!)
    }

    @Test
    fun `Add new users on-the-fly` () {
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
    fun `Modify user permissions during RPC session` () {

        db.insert(UserAndRoles(
                username = "user3",
                password = "bar",
                roles = emptyList()))


        client.start("user3", "bar").use {
            val proxy = it.proxy

            assertFailsWith (
                    PermissionException::class,
                    "This user should not be authorized to call 'nodeInfo'") {
                proxy.stateMachinesFeed()
            }

            db.addRoleToUser("user3", "default")
            proxy.stateMachinesFeed()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }
}

private fun CordaRPCClient.start(username: String, password: String) =
    this.start(username, password)

private data class UserAndRoles(val username: String, val password: String, val roles: List<String>)
private data class RoleAndPermission(val role: String, val permissions: List<String>)

private class MockUsersDB : AutoCloseable {

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

    fun insert(roleAndPermission: RoleAndPermission) {
        val (role, permissions) = roleAndPermission
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

    inline private fun session (statement : (Statement) -> Unit) {
        DriverManager.getConnection(jdbcUrl).use {
            it.autoCommit = false
            it.createStatement().use(statement)
            it.commit()
        }
    }

    constructor(name: String,
                users: List<UserAndRoles> = emptyList(),
                roleAndPermissions: List<RoleAndPermission> = emptyList()) {

        jdbcUrl = "jdbc:h2:mem:${name};DB_CLOSE_DELAY=-1"

        session {
            it.execute(DB_CREATE_SCHEMA)
        }

        require (users.map { it.username }.toSet().size == users.size) {
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