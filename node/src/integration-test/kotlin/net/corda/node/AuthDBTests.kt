/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.PermissionException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.DataSourceFactory
import net.corda.node.internal.NodeWithInfo
import net.corda.node.services.Permissions
import net.corda.node.services.config.PasswordEncryption
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.internal.NodeBasedTest
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.shiro.authc.credential.DefaultPasswordService
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.sql.Statement
import java.util.*
import javax.sql.DataSource
import kotlin.test.assertFailsWith

/*
 * Starts Node's instance configured to load clients credentials and permissions from an external DB, then
 * check authentication/authorization of RPC connections.
 */
@RunWith(Parameterized::class)
class AuthDBTests : NodeBasedTest() {

    private lateinit var node: NodeWithInfo
    private lateinit var client: CordaRPCClient
    private lateinit var db: UsersDB

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName())

        private const val cacheExpireAfterSecs: Long = 1

        @JvmStatic
        @Parameterized.Parameters(name = "password encryption format = {0}")
        fun encFormats() = arrayOf(PasswordEncryption.NONE, PasswordEncryption.SHIRO_1_CRYPT)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    @Parameterized.Parameter
    lateinit var passwordEncryption: PasswordEncryption

    @Before
    fun setup() {
        db = UsersDB(
                name = "SecurityDataSourceTestDB",
                users = listOf(UserAndRoles(username = "user",
                        password = encodePassword("foo", passwordEncryption),
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

        val securityConfig = mapOf(
                "security" to mapOf(
                        "authService" to mapOf(
                                "dataSource" to mapOf(
                                        "type" to "DB",
                                        "passwordEncryption" to passwordEncryption.toString(),
                                        "connection" to mapOf(
                                                "jdbcUrl" to db.jdbcUrl,
                                                "username" to "",
                                                "password" to "",
                                                "driverClassName" to "org.h2.Driver"
                                        )
                                ),
                                "options" to mapOf(
                                        "cache" to mapOf(
                                                "expireAfterSecs" to cacheExpireAfterSecs,
                                                "maxEntries" to 50
                                        )
                                )
                        )
                )
        )

        node = startNode(ALICE_NAME, rpcUsers = emptyList(), configOverrides = securityConfig)
        client = CordaRPCClient(node.node.configuration.rpcOptions.address)
    }

    @Test
    fun `login with correct credentials`() {
        client.start("user", "foo").close()
    }

    @Test
    fun `login with wrong credentials`() {
        client.start("user", "foo").close()
        assertFailsWith(
                ActiveMQSecurityException::class,
                "Login with incorrect password should fail") {
            client.start("user", "bar").close()
        }
        assertFailsWith(
                ActiveMQSecurityException::class,
                "Login with unknown username should fail") {
            client.start("X", "foo").close()
        }
    }

    @Test
    fun `check flow permissions are respected`() {
        client.start("user", "foo").use {
            val proxy = it.proxy
            proxy.startFlowDynamic(DummyFlow::class.java)
            proxy.startTrackedFlowDynamic(DummyFlow::class.java)
            proxy.startFlow(AuthDBTests::DummyFlow)
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

    @Test
    fun `Add new users dynamically`() {
        assertFailsWith(
                ActiveMQSecurityException::class,
                "Login with incorrect password should fail") {
            client.start("user2", "bar").close()
        }

        db.insert(UserAndRoles(
                username = "user2",
                password = encodePassword("bar"),
                roles = listOf("default")))

        client.start("user2", "bar").close()
    }

    @Test
    fun `Modify user permissions during RPC session`() {
        db.insert(UserAndRoles(
                username = "user3",
                password = encodePassword("bar"),
                roles = emptyList()))

        client.start("user3", "bar").use {
            val proxy = it.proxy
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to call 'nodeInfo'") {
                proxy.stateMachinesFeed()
            }
            db.addRoleToUser("user3", "default")
            Thread.sleep(1500)
            proxy.stateMachinesFeed()
        }
    }

    @Test
    fun `Revoke user permissions during RPC session`() {
        db.insert(UserAndRoles(
                username = "user4",
                password = encodePassword("test"),
                roles = listOf("default")))

        client.start("user4", "test").use {
            val proxy = it.proxy
            proxy.stateMachinesFeed()
            db.deleteUser("user4")
            Thread.sleep(1500)
            assertFailsWith(
                    PermissionException::class,
                    "This user should not be authorized to call 'nodeInfo'") {
                proxy.stateMachinesFeed()
            }
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class DummyFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = Unit
    }

    @After
    override fun tearDown() {
         db.close()
    }

    private fun encodePassword(s: String) = encodePassword(s, passwordEncryption)
}

private data class UserAndRoles(val username: String, val password: String, val roles: List<String>)
private data class RoleAndPermissions(val role: String, val permissions: List<String>)

/*
 * Manage in-memory DB mocking a users database with the schema expected by Node's security manager
 */
private class UsersDB(name: String, users: List<UserAndRoles> = emptyList(), roleAndPermissions: List<RoleAndPermissions> = emptyList()) : AutoCloseable {
    val jdbcUrl = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1"

    companion object {
        const val DB_CREATE_SCHEMA = """
            CREATE TABLE users (username VARCHAR(256), password TEXT);
            CREATE TABLE user_roles (username VARCHAR(256), role_name VARCHAR(256));
            CREATE TABLE roles_permissions (role_name VARCHAR(256), permission TEXT);
            """
    }

    fun insert(user: UserAndRoles) {
        session {
            it.execute("INSERT INTO users VALUES ('${user.username}', '${user.password}')")
            for (role in user.roles) {
                it.execute("INSERT INTO user_roles VALUES ('${user.username}', '$role')")
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

    fun deleteUser(username: String) {
        session {
            it.execute("DELETE FROM users WHERE username = '$username'")
            it.execute("DELETE FROM user_roles WHERE username = '$username'")
        }
    }

    private val dataSource: DataSource
    private inline fun session(statement: (Statement) -> Unit) {
        dataSource.connection.use {
            it.autoCommit = false
            it.createStatement().use(statement)
            it.commit()
        }
    }

    init {
        dataSource = DataSourceFactory.createDataSource(Properties().apply {
            put("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
            put("dataSource.url", jdbcUrl)
        }, false)
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
        dataSource.connection.use {
            it.createStatement().use {
                it.execute("DROP ALL OBJECTS")
            }
        }
    }
}

/*
 * Sample of hardcoded hashes to watch for format backward compatibility
 */
private val hashedPasswords = mapOf(
        PasswordEncryption.SHIRO_1_CRYPT to mapOf(
                "foo" to "\$shiro1\$SHA-256$500000\$WSiEVj6q8d02sFcCk1dkoA==\$MBkU/ghdD9ovoDerdzNfkXdP9Bdhmok7tidvVIqGzcA=",
                "bar" to "\$shiro1\$SHA-256$500000\$Q6dmdY1uVMm0LYAWaOHtCA==\$u7NbFaj9tHf2RTW54jedLPiOiGjJv0RVEPIjVquJuYY=",
                "test" to "\$shiro1\$SHA-256$500000\$F6CWSFDDxGTlzvREwih8Gw==\$DQhyAPoUw3RdvNYJ1aubCnzEIXm+szGQ3HplaG+euz8="))

/*
 * A functional object for producing password encoded according to the given scheme.
 */
private fun encodePassword(s: String, format: PasswordEncryption) = when (format) {
    PasswordEncryption.NONE -> s
    PasswordEncryption.SHIRO_1_CRYPT -> hashedPasswords[format]!![s] ?: DefaultPasswordService().encryptPassword(s.toCharArray())
}