package net.corda.demobench.views

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.util.*
import javafx.application.Platform
import javafx.embed.swing.SwingNode
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javax.swing.SwingUtilities
import net.corda.demobench.pty.R3Pty
import net.corda.demobench.ui.CloseableTab
import tornadofx.*

class DemoBenchView : View("Corda Demo Bench") {

    override val root by fxml<Parent>()

    val addNodeButton by fxid<Button>()
    val nodeTabPane by fxid<TabPane>()

    val settingsProvider = TerminalSettingsProvider()

    init {
        importStylesheet("/net/corda/demobench/style.css")

        primaryStage.setOnCloseRequest {
            log.info("Exiting")

            closeAllTabs()
            Platform.exit()
        }

        addNodeButton.setOnAction {
            val nodeTab = createNode()
            nodeTabPane.selectionModel.select(nodeTab)
        }
        addNodeButton.fire()
    }

    private fun closeAllTabs() {
        ArrayList<Tab>(nodeTabPane.tabs).forEach {
            (it as CloseableTab).requestClose()
        }
    }

    fun createNode(): CloseableTab {
        val pty = R3Pty("Banksy", settingsProvider, java.awt.Dimension(160, 80))
        val nodeTabView = NodeTabView(pty.name)
        val nodeTab = CloseableTab(pty.name, nodeTabView.root)

        // Ensure that we close the terminal along with the tab.
        nodeTab.setOnCloseRequest {
            pty.close()
        }

        val swingTerminal = SwingNode()
        SwingUtilities.invokeLater({
            swingTerminal.content = pty.terminal
            swingTerminal.setOnMouseClicked {
                swingTerminal.requestFocus()
            }
        })

        nodeTabPane.tabs.add(nodeTab)
        nodeTab.content.add(swingTerminal)

        pty.run("/bin/bash", "--login")

        return nodeTab
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
