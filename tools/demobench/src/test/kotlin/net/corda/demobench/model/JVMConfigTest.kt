package net.corda.demobench.model

import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.*

class JVMConfigTest {

    private val jvm = JVMConfig()

    @Test
    fun `test Java path`() {
        assertTrue(Files.isExecutable(jvm.javaPath))
    }

    @Test
    fun `test application directory`() {
        assertTrue(Files.isDirectory(jvm.applicationDir))
    }

    @Test
    fun `test user home`() {
        assertTrue(Files.isDirectory(jvm.userHome))
    }

    @Test
    fun `test command for Jar`() {
        val command = jvm.commandFor(Paths.get("testapp.jar"), "arg1", "arg2")
        val java = jvm.javaPath
        assertEquals(listOf(java.toString(), "-jar", "testapp.jar", "arg1", "arg2"), command)
    }

}