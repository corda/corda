package net.corda.demobench.model

import com.jediterm.terminal.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals

class JVMConfigTest {
    private val jvm = JVMConfig()

    @Test
    fun `test Java path`() {
        assertThat(jvm.javaPath.onFileSystem()).isExecutable()
    }

    @Test
    fun `test application directory`() {
        assertThat(jvm.applicationDir).isDirectory()
    }

    @Test
    fun `test user home`() {
        assertThat(jvm.userHome).isDirectory()
    }

    @Test
    fun `test command for Jar`() {
        val command = jvm.commandFor(Paths.get("testapp.jar"), "arg1", "arg2")
        val java = jvm.javaPath
        assertEquals(listOf(java.toString(), "-jar", "testapp.jar", "arg1", "arg2"), command)
    }

    @Test
    fun `test process for Jar`() {
        val process = jvm.processFor(Paths.get("testapp.jar"), "arg1", "arg2", "arg3")
        val java = jvm.javaPath
        assertEquals(listOf(java.toString(), "-jar", "testapp.jar", "arg1", "arg2", "arg3"), process.command())
    }

    private fun Path.onFileSystem(): Path = if (UIUtil.isWindows) parent.resolve(Paths.get("$fileName.exe")) else this
}
