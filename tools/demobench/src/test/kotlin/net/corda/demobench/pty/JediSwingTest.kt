package net.corda.demobench.pty

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JFrame
import javax.swing.WindowConstants
import net.corda.core.utilities.loggerFor
import org.junit.After
import org.junit.Ignore
import org.junit.Test

@Ignore
class JediSwingTest {
    private companion object {
        val java: Path = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath()
        val log = loggerFor<JediSwingTest>()
    }

    private lateinit var pty: R3Pty

    @After
    fun done() {
        pty.close()
    }

    @Test
    fun `swing jedi input`() {
        val frame = JFrame("JediTerm Swing")
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE

        val settings = object : DefaultSettingsProvider() {
            override fun getDefaultStyle(): TextStyle = TextStyle(TerminalColor.WHITE, TerminalColor.BLACK)

            override fun getTerminalFontSize(): Float = 20.0f

            override fun emulateX11CopyPaste(): Boolean = true
        }

        pty = R3Pty("Testing Input", settings, Dimension(164, 40), this::shutdown)

        frame.contentPane.add(pty.terminal, BorderLayout.CENTER)

        frame.pack()
        frame.isLocationByPlatform = true
        frame.isResizable = true
        frame.isVisible = true

        pty.run(
            arrayOf(java.toString(), "-jar", "getinput.jar"),
            emptyMap(),
            "build/libs/"
        )

        pty.waitFor()
    }

    private fun shutdown() = log.info("EXITED")
}
