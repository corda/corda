package net.corda.node.services


import net.corda.core.context.AuthServiceId
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.services.config.PasswordEncryption
import net.corda.node.services.config.SecurityDataSourceConfig
import net.corda.node.services.config.SecurityDataSourceType
import net.corda.nodeapi.User
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.*
import kotlin.test.assertFalse

class RPCSecurityManagerTest {

    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }

    // FIXME: remove!
    @Test
    fun `Authentication with credentials on Azure SQL Server`() {

        val sourceConfig = SecurityDataSourceConfig(
                type=SecurityDataSourceType.JDBC,
                passwordEncryption = PasswordEncryption.NONE,
                dataSourceProperties = Properties().apply {
                        setProperty("jdbcUrl", "jdbc:sqlserver://szymon.database.windows.net:1433;databaseName=szymon;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30")
                        setProperty("username",  "shiro")
                        setProperty("password", "yourStrong(!)Password")
                        setProperty("driverClassName", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
                }
        )

        val manager = RPCSecurityManagerImpl(AuthServiceId("TEST"), listOf(sourceConfig))

        val subject = manager.authenticate("test", "test".toCharArray())

        assertFalse (subject.isPermitted("nodeInfo"))

        val admin = manager.authenticate("admin", "admin".toCharArray())

        assert (admin.isPermitted("nodeInfo"))
    }

    private fun configWithRPCUsername(username: String) {
        RPCSecurityManagerImpl.buildInMemory(
                users = listOf(User(username, "password", setOf())),
                id = AuthServiceId("TEST"))
    }
}