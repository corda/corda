package net.corda.node.services

import com.typesafe.config.ConfigFactory
import net.corda.node.services.config.FullNodeConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.nio.file.Paths

class RPCUserServiceImplTest {

    @Test
    fun `missing config`() {
        val service = loadWithContents("{}")
        assertThat(service.getUser("user")).isNull()
        assertThat(service.users).isEmpty()
    }

    @Test
    fun `no users`() {
        val service = loadWithContents("rpcUsers : []")
        assertThat(service.getUser("user")).isNull()
        assertThat(service.users).isEmpty()
    }

    @Test
    fun `no permissions`() {
        val service = loadWithContents("rpcUsers : [{ user=user1, password=letmein }]")
        val expectedUser = User("user1", "letmein", permissions = emptySet())
        assertThat(service.getUser("user1")).isEqualTo(expectedUser)
        assertThat(service.users).containsOnly(expectedUser)
    }

    @Test
    fun `single permission, which is in lower case`() {
        val service = loadWithContents("rpcUsers : [{ user=user1, password=letmein, permissions=[cash] }]")
        assertThat(service.getUser("user1")?.permissions).containsOnly("cash")
    }

    @Test
    fun `two permissions, which are upper case`() {
        val service = loadWithContents("rpcUsers : [{ user=user1, password=letmein, permissions=[CASH, ADMIN] }]")
        assertThat(service.getUser("user1")?.permissions).containsOnly("CASH", "ADMIN")
    }

    @Test
    fun `two users`() {
        val service = loadWithContents("""rpcUsers : [
        { user=user, password=password, permissions=[ADMIN] }
        { user=user2, password=password2, permissions=[] }
        ]""")
        val user1 = User("user", "password", permissions = setOf("ADMIN"))
        val user2 = User("user2", "password2", permissions = emptySet())
        assertThat(service.getUser("user")).isEqualTo(user1)
        assertThat(service.getUser("user2")).isEqualTo(user2)
        assertThat(service.users).containsOnly(user1, user2)
    }

    @Test
    fun `unknown user`() {
        val service = loadWithContents("rpcUsers : [{ user=user1, password=letmein }]")
        assertThat(service.getUser("test")).isNull()
    }

    @Test
    fun `Artemis special characters not permitted in usernames`() {
        assertThatThrownBy { loadWithContents("rpcUsers : [{ user=user.1, password=letmein }]") }.hasMessageContaining(".")
        assertThatThrownBy { loadWithContents("rpcUsers : [{ user=user*1, password=letmein }]") }.hasMessageContaining("*")
        assertThatThrownBy { loadWithContents("""rpcUsers : [{ user="user#1", password=letmein }]""") }.hasMessageContaining("#")
    }

    private fun loadWithContents(configString: String): RPCUserServiceImpl {
        return RPCUserServiceImpl(FullNodeConfiguration(Paths.get("."), ConfigFactory.parseString(configString)))
    }
}