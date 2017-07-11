package net.corda.demobench.views

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Dimension
import java.net.URI
import java.util.logging.Level
import javax.swing.SwingUtilities
import javafx.application.Platform
import javafx.embed.swing.SwingNode
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.util.Duration
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.commonName
import net.corda.core.match
import net.corda.core.then
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.vault.PageSpecification
import net.corda.demobench.explorer.ExplorerController
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.NodeController
import net.corda.demobench.model.NodeState
import net.corda.demobench.pty.R3Pty
import net.corda.demobench.rpc.NodeRPC
import net.corda.demobench.ui.PropertyLabel
import net.corda.demobench.web.DBViewer
import net.corda.demobench.web.WebServerController
import rx.Subscription
import rx.schedulers.Schedulers
import tornadofx.*

class NodeTerminalView : Fragment() {
    override val root by fxml<VBox>()

    private companion object {
        val pageSpecification = PageSpecification(0, 1)
    }

    private val nodeController by inject<NodeController>()
    private val explorerController by inject<ExplorerController>()
    private val webServerController by inject<WebServerController>()

    private val nodeName by fxid<Label>()
    private val states by fxid<PropertyLabel>()
    private val transactions by fxid<PropertyLabel>()
    private val balance by fxid<PropertyLabel>()

    private val header by fxid<HBox>()
    private val viewDatabaseButton by fxid<Button>()
    private val launchWebButton by fxid<Button>()
    private val launchExplorerButton by fxid<Button>()

    private val subscriptions: MutableList<Subscription> = mutableListOf()
    private var txCount: Int = 0
    private var stateCount: Int = 0
    private var isDestroyed: Boolean = false
    private val explorer = explorerController.explorer()
    private val webServer = webServerController.webServer()
    private val viewer = DBViewer()
    private var rpc: NodeRPC? = null
    private var pty: R3Pty? = null
    private lateinit var logo: ImageView
    private lateinit var swingTerminal: SwingNode

    fun open(config: NodeConfig, onExit: (Int) -> Unit) {
        nodeName.text = config.legalName.commonName

        swingTerminal = SwingNode()
        swingTerminal.setOnMouseClicked {
            swingTerminal.requestFocus()
        }

        logo = ImageView(resources["corda-logo-square-trans.png"])
        logo.opacity = 0.0
        swingTerminal.styleClass += "terminal-widget"
        val stack = StackPane(logo, swingTerminal)
        root.children.add(stack)
        root.isVisible = true

        SwingUtilities.invokeLater({
            val r3pty = R3Pty(config.legalName, TerminalSettingsProvider(), Dimension(160, 80), onExit)
            pty = r3pty

            if (nodeController.runCorda(r3pty, config)) {
                swingTerminal.content = r3pty.terminal

                configureDatabaseButton(config)
                configureExplorerButton(config)
                configureWebButton(config)

                /*
                 * Start RPC client that will update node statistics on UI.
                 */
                rpc = launchRPC(config)

                /*
                 * Check whether the PTY has exited unexpectedly,
                 * and close the RPC client if it has.
                 */
                if (!r3pty.isConnected) {
                    log.severe("Node '${config.legalName}' has failed to start.")
                    swingTerminal.content = null
                    rpc?.close()
                }
            }
        })
    }

    /*
     * We only want to run one explorer for each node.
     * So disable the "launch" button when we have
     * launched the explorer and only reenable it when
     * the explorer has exited.
     */
    private fun configureExplorerButton(config: NodeConfig) {
        launchExplorerButton.setOnAction {
            launchExplorerButton.isDisable = true

            explorer.open(config, onExit = {
                launchExplorerButton.isDisable = false
            })
        }
    }

    private fun configureDatabaseButton(config: NodeConfig) {
        viewDatabaseButton.setOnAction {
            viewer.openBrowser(config.h2Port)
        }
    }

    private var webURL: URI? = null
    private var launchingWebServer = false

    /*
     * We only want to run one web server for each node.
     * So disable the "launch" button when we have
     * launched the web server and only reenable it when
     * the web server has exited.
     */
    private fun configureWebButton(config: NodeConfig) {
        launchWebButton.setOnAction {
            if (webURL != null) {
                app.hostServices.showDocument(webURL.toString())
                return@setOnAction
            }

            // We use our own way to suppress clicks whilst starting as a quick style hack, as the progress spinner
            // gets very dim when placed inside a disabled button.
            if (launchingWebServer)
                return@setOnAction

            launchingWebServer = true
            val oldLabel = launchWebButton.text
            launchWebButton.text = ""
            launchWebButton.graphic = ProgressIndicator()

            log.info("Starting web server for ${config.legalName}")
            webServer.open(config).then {
                Platform.runLater {
                    launchWebButton.graphic = null
                }
                it.match({
                    log.info("Web server for ${config.legalName} started on $it")
                    Platform.runLater {
                        webURL = it
                        launchWebButton.text = "Reopen\nweb site"
                        app.hostServices.showDocument(it.toString())
                    }
                }, {
                    launchWebButton.text = oldLabel
                })
            }
        }
    }

    private fun launchRPC(config: NodeConfig) = NodeRPC(
        config = config,
        start = this::initialise,
        invoke = this::pollCashBalances
    )

    private fun initialise(config: NodeConfig, ops: CordaRPCOps) {
        try {
            val (txInit, txNext) = ops.verifiedTransactionsFeed()
            val (stateInit, stateNext) = ops.vaultTrackBy<ContractState>(paging = pageSpecification)

            txCount = txInit.size
            stateCount = stateInit.totalStatesAvailable

            Platform.runLater {
                logo.opacityProperty().animate(1.0, Duration.seconds(2.5))
                transactions.value = txCount.toString()
                states.value = stateCount.toString()
            }

            val fxScheduler = Schedulers.from(Platform::runLater)
            subscriptions.add(txNext.observeOn(fxScheduler).subscribe {
                transactions.value = (++txCount).toString()
            })
            subscriptions.add(stateNext.observeOn(fxScheduler).subscribe {
                stateCount += (it.produced.size - it.consumed.size)
                states.value = stateCount.toString()
            })
        } catch (e: Exception) {
            log.log(Level.WARNING, "RPC failed: ${e.message}", e)
        }

        config.state = NodeState.RUNNING
        log.info("Node '${config.legalName}' is now ready.")

        header.isDisable = false
    }

    private fun pollCashBalances(ops: CordaRPCOps) {
        try {
            val cashBalances = ops.getCashBalances().entries.joinToString(
                    separator = ", ",
                    transform = { e -> e.value.toString() }
            )

            Platform.runLater {
                balance.value = if (cashBalances.isNullOrEmpty()) "0" else cashBalances
            }
        } catch (e: ClassNotFoundException) {
            // TODO: Remove this special case once Rick's serialisation work means we can deserialise states that weren't on our own classpath.
        } catch (e: Exception) {
            log.log(Level.WARNING, "Cash balance RPC failed: ${e.message}", e)
        }
    }

    fun shutdown() {
        header.isDisable = true
        subscriptions.forEach {
            // Don't allow any exceptions here to halt tab destruction.
            try { it.unsubscribe() } catch (e: Exception) {}
        }
        webServer.close()
        explorer.close()
        viewer.close()
        rpc?.close()
    }

    fun destroy() {
        if (!isDestroyed) {
            shutdown()
            pty?.close()
            isDestroyed = true
        }
    }

    fun takeFocus() {
        // Request focus. This also forces a repaint, without this, for some reason the terminal has gone to sleep
        // and is no longer updating in response to new data from the pty.
        Platform.runLater {
            swingTerminal.requestFocus()
        }
    }

    class TerminalSettingsProvider : DefaultSettingsProvider() {
        override fun getDefaultStyle() = TextStyle(TerminalColor.WHITE, TerminalColor.rgb(50, 50, 50))
        override fun emulateX11CopyPaste() = true
    }
}
