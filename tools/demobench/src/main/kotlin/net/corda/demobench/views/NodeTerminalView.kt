package net.corda.demobench.views

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Dimension
import javafx.embed.swing.SwingNode
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javax.swing.SwingUtilities
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.NodeController
import net.corda.demobench.pty.R3Pty
import net.corda.demobench.ui.PropertyLabel
import tornadofx.Fragment
import tornadofx.vgrow

class NodeTerminalView : Fragment() {
    override val root by fxml<VBox>()

    private val controller by inject<NodeController>()

    private val nodeName by fxid<Label>()
    private val p2pPort by fxid<PropertyLabel>()
    private val states by fxid<PropertyLabel>()
    private val transactions by fxid<PropertyLabel>()
    private val balance by fxid<PropertyLabel>()

    private val viewDatabaseButton by fxid<Button>()
    private val launchExplorerButton by fxid<Button>()

    var pty : R3Pty? = null

    fun open(config: NodeConfig) {
        nodeName.text = config.legalName
        p2pPort.value = config.p2pPort.toString()

        val swingTerminal = SwingNode()
        swingTerminal.setOnMouseClicked {
            swingTerminal.requestFocus()
        }

        root.children.add(swingTerminal)
        root.isVisible = true

        SwingUtilities.invokeLater({
            val r3pty = R3Pty(config.legalName, TerminalSettingsProvider(), Dimension(160, 80))
            pty = r3pty

            swingTerminal.content = r3pty.terminal
            controller.runCorda(r3pty, config)
        })
    }

    fun close() {
        pty?.close()
    }

    fun refreshTerminal() {
        SwingUtilities.invokeLater {
            // TODO
        }
    }

    init {
        root.vgrow = Priority.ALWAYS
    }

    class TerminalSettingsProvider : DefaultSettingsProvider() {
        override fun getDefaultStyle(): TextStyle {
            return TextStyle(TerminalColor.WHITE, TerminalColor.BLACK)
        }

        override fun emulateX11CopyPaste(): Boolean {
            return true
        }
    }

}
