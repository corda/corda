package com.r3corda.node.services

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.r3corda.core.writeLines
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Test

class PropertiesFileRPCUserServiceTest {

    private val fileSystem = Jimfs.newFileSystem(unix())
    private val file = fileSystem.getPath("users.properties")

    @After
    fun cleanUp() {
        fileSystem.close()
    }

    @Test
    fun `file doesn't exist`() {
        val service = PropertiesFileRPCUserService(file)
        assertThat(service.getUser("user")).isNull()
        assertThat(service.users).isEmpty()
    }

    @Test
    fun `empty file`() {
        val service = loadWithContents()
        assertThat(service.getUser("user")).isNull()
        assertThat(service.users).isEmpty()
    }

    @Test
    fun `no permissions`() {
        val service = loadWithContents("user=password")
        assertThat(service.getUser("user")).isEqualTo(User("user", "password", permissions = emptySet()))
        assertThat(service.users).containsOnly(User("user", "password", permissions = emptySet()))
    }

    @Test
    fun `single permission, which is in lower case`() {
        val service = loadWithContents("user=password,cash")
        assertThat(service.getUser("user")?.permissions).containsOnly("CASH")
    }

    @Test
    fun `two permissions, which are upper case`() {
        val service = loadWithContents("user=password,CASH,ADMIN")
        assertThat(service.getUser("user")?.permissions).containsOnly("CASH", "ADMIN")
    }

    @Test
    fun `two users`() {
        val service = loadWithContents("user=password,ADMIN", "user2=password2")
        assertThat(service.getUser("user")).isNotNull()
        assertThat(service.getUser("user2")).isNotNull()
        assertThat(service.users).containsOnly(
                User("user", "password", permissions = setOf("ADMIN")),
                User("user2", "password2", permissions = emptySet()))
    }

    @Test
    fun `unknown user`() {
        val service = loadWithContents("user=password")
        assertThat(service.getUser("test")).isNull()
    }

    @Test
    fun `Artemis special characters not permitted in usernames`() {
        assertThatThrownBy { loadWithContents("user.name=password") }
        assertThatThrownBy { loadWithContents("user*name=password") }
        assertThatThrownBy { loadWithContents("user#name=password") }
    }

    private fun loadWithContents(vararg lines: String): PropertiesFileRPCUserService {
        file.writeLines(lines.asList())
        return PropertiesFileRPCUserService(file)
    }
}