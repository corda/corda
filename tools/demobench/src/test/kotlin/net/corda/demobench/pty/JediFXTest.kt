package net.corda.demobench.pty

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import javafx.application.Application
import javafx.application.Platform
import javafx.embed.swing.SwingNode
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import javax.swing.SwingUtilities
import net.corda.core.utilities.loggerFor
import org.junit.Ignore
import org.junit.Test

@Ignore
class JediFXTest {
    private companion object {
        val java: Path = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath()
        val log = loggerFor<JediFXTest>()
    }

    @Test
    fun `javafx jedi output`() {
        Application.launch(JediApp::class.java)
    }

    class JediApp : Application() {
        private lateinit var pty: R3Pty

        override fun start(stage: Stage) {
            stage.setOnCloseRequest {
                shutdown()
            }

            stage.title = "JediTerm JavaFX"

            val settings = object : DefaultSettingsProvider() {
                override fun getDefaultStyle(): TextStyle = TextStyle(TerminalColor.WHITE, TerminalColor.BLACK)

                override fun getTerminalFontSize(): Float = 20.0f

                override fun emulateX11CopyPaste(): Boolean = true
            }

            val swingTerminal = SwingNode()
            val root = StackPane()
            root.children.add(swingTerminal)

            SwingUtilities.invokeLater({
                val r3Pty = R3Pty("Testing Input", settings, Dimension(164, 40), this::shutdown)
                pty = r3Pty

                swingTerminal.content = r3Pty.terminal

                r3Pty.run(
                    arrayOf(java.toString(), "-jar", "getinput.jar"),
                    emptyMap(),
                    "build/libs/"
                )
            })

            stage.scene = Scene(root, 1000.0, 500.0)
            stage.show()
        }

        override fun stop() = pty.close()

        private fun shutdown() {
            log.info("EXITED")
            Platform.exit()
        }
    }
}