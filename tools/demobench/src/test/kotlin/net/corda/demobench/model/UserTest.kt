package net.corda.demobench.model

import net.corda.nodeapi.User
import org.junit.Test
import kotlin.test.assertEquals

class UserTest {

    @Test
    fun createFromEmptyMap() {
        val user = toUser(emptyMap())
        assertEquals("none", user.username)
        assertEquals("none", user.password)
        assertEquals(emptySet(), user.permissions)
    }

    @Test
    fun createFromMap() {
        val map = mapOf(
                "username" to "MyName",
                "password" to "MyPassword",
                "permissions" to listOf("Flow.MyFlow")
        )
        val user = toUser(map)
        assertEquals("MyName", user.username)
        assertEquals("MyPassword", user.password)
        assertEquals(setOf("Flow.MyFlow"), user.permissions)
    }

    @Test
    fun userToMap() {
        val user = User("MyName", "MyPassword", setOf("Flow.MyFlow"))
        val map = user.toMap()
        assertEquals("MyName", map["username"])
        assertEquals("MyPassword", map["password"])
        assertEquals(listOf("Flow.MyFlow"), map["permissions"])
    }

    @Test
    fun `default user`() {
        val user = user("guest")
        assertEquals("guest", user.username)
        assertEquals("letmein", user.password)
        assertEquals(setOf("ALL"), user.permissions)
    }

}
