package net.corda.demobench.views

import javafx.application.Platform
import javafx.scene.control.SelectionMode.MULTIPLE
import javafx.scene.input.KeyCode
import javafx.scene.layout.Pane
import javafx.stage.FileChooser
import javafx.util.converter.NumberStringConverter
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.demobench.model.*
import net.corda.demobench.ui.CloseableTab
import tornadofx.*
import java.nio.file.Path
import java.text.DecimalFormat
import java.util.*

class NodeTabView : Fragment() {
    override val root = stackpane {}

    private val main by inject<DemoBenchView>()
    private val showConfig by param(true)

    private companion object : Component() {
        const val textWidth = 200.0
        const val numberWidth = 100.0
        const val maxNameLength = 15

        val integerFormat = DecimalFormat()
        val notNumber = "[^\\d]".toRegex()

        val jvm by inject<JVMConfig>()

        init {
            integerFormat.isGroupingUsed = false
        }
    }

    private val nodeController by inject<NodeController>()
    private val serviceController by inject<ServiceController>()
    private val chooser = FileChooser()

    private val model = NodeDataModel()
    private val cordapps = LinkedList<Path>().observable()
    private val availableServices: List<String> = if (nodeController.hasNetworkMap()) serviceController.services else serviceController.notaries

    private val nodeTerminalView = find<NodeTerminalView>()
    private val nodeConfigView = stackpane {
        isVisible = showConfig

        form {
            fieldset("Configuration") {
                isFillWidth = false

                field("Node Name", op = { nodeNameField() })
                field("Nearest City", op = { nearestCityField() })
                field("P2P Port", op = { p2pPortField() })
                field("RPC port", op = { rpcPortField() })
                field("Web Port", op = { webPortField() })
                field("Database Port", op = { databasePortField() })
            }

            hbox {
                styleClass.addAll("node-panel")

                fieldset("Services") {
                    styleClass.addAll("services-panel")

                    listview(availableServices.observable()) {
                        selectionModel.selectionMode = MULTIPLE
                        model.item.extraServices.set(selectionModel.selectedItems)
                    }
                }

                fieldset("CorDapps") {
                    styleClass.addAll("cordapps-panel")

                    listview(cordapps) {
                        setOnKeyPressed { key ->
                            if ((key.code == KeyCode.DELETE) && !selectionModel.isEmpty) {
                                cordapps.remove(selectionModel.selectedItem)
                            }
                            key.consume()
                        }
                    }
                    button("Add CorDapp") {
                        setOnAction {
                            val app = (chooser.showOpenDialog(null) ?: return@setOnAction).toPath()
                            if (!cordapps.contains(app)) {
                                cordapps.add(app)
                            }
                        }
                    }
                }
            }

            button("Create Node") {
                setOnAction {
                    if (model.validate()) {
                        launch()
                        main.enableAddNodes()
                        main.enableSaveProfile()
                    }
                }
            }
        }
    }

    val nodeTab = CloseableTab("New Node", root)

    init {
        // Ensure that we destroy the terminal along with the tab.
        nodeTab.setOnCloseRequest {
            nodeTerminalView.destroy()
        }

        root.add(nodeConfigView)
        root.add(nodeTerminalView)

        model.legalName.value = if (nodeController.hasNetworkMap()) "" else DUMMY_NOTARY.name
        model.p2pPort.value = nodeController.nextPort
        model.rpcPort.value = nodeController.nextPort
        model.webPort.value = nodeController.nextPort
        model.h2Port.value = nodeController.nextPort

        chooser.title = "CorDapps"
        chooser.initialDirectory = jvm.dataHome.toFile()
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("CorDapps (*.jar)", "*.jar", "*.JAR"))
    }

    private fun Pane.nodeNameField() = textfield(model.legalName) {
        minWidth = textWidth
        validator {
            if (it == null) {
                error("Node name is required")
            } else {
                val name = it.trim()
                if (name.isEmpty()) {
                    error("Node name is required")
                } else if (nodeController.nameExists(name)) {
                    error("Node with this name already exists")
                } else if (name.length > maxNameLength) {
                    error("Name is too long")
                } else {
                    null
                }
            }
        }
    }

    private fun Pane.nearestCityField() = textfield(model.nearestCity) {
        minWidth = textWidth
        validator {
            if (it == null) {
                error("Nearest city is required")
            } else if (it.trim().isEmpty()) {
                error("Nearest city is required")
            } else {
                null
            }
        }
    }

    private fun Pane.p2pPortField() = textfield(model.p2pPort, NumberStringConverter(integerFormat)) {
        minWidth = numberWidth
        validator {
            if ((it == null) || it.isEmpty()) {
                error("Port number required")
            } else if (it.contains(notNumber)) {
                error("Invalid port number")
            } else {
                val port = it.toInt()
                if (!nodeController.isPortAvailable(port)) {
                    error("Port $it is unavailable")
                } else if (port == model.rpcPort.value) {
                    error("Clashes with RPC port")
                } else if (port == model.webPort.value) {
                    error("Clashes with web port")
                } else if (port == model.h2Port.value) {
                    error("Clashes with database port")
                } else {
                    null
                }
            }
        }
    }

    private fun Pane.rpcPortField() = textfield(model.rpcPort, NumberStringConverter(integerFormat)) {
        minWidth = 100.0
        validator {
            if ((it == null) || it.isEmpty()) {
                error("Port number required")
            } else if (it.contains(notNumber)) {
                error("Invalid port number")
            } else {
                val port = it.toInt()
                if (!nodeController.isPortAvailable(port)) {
                    error("Port $it is unavailable")
                } else if (port == model.p2pPort.value) {
                    error("Clashes with P2P port")
                } else if (port == model.webPort.value) {
                    error("Clashes with web port")
                } else if (port == model.h2Port.value) {
                    error("Clashes with database port")
                } else {
                    null
                }
            }
        }
    }

    private fun Pane.webPortField() = textfield(model.webPort, NumberStringConverter(integerFormat)) {
        minWidth = numberWidth
        validator {
            if ((it == null) || it.isEmpty()) {
                error("Port number required")
            } else if (it.contains(notNumber)) {
                error("Invalid port number")
            } else {
                val port = it.toInt()
                if (!nodeController.isPortAvailable(port)) {
                    error("Port $it is unavailable")
                } else if (port == model.p2pPort.value) {
                    error("Clashes with P2P port")
                } else if (port == model.rpcPort.value) {
                    error("Clashes with RPC port")
                } else if (port == model.h2Port.value) {
                    error("Clashes with database port")
                } else {
                    null
                }
            }
        }
    }

    private fun Pane.databasePortField() = textfield(model.h2Port, NumberStringConverter(integerFormat)) {
        minWidth = numberWidth
        validator {
            if ((it == null) || it.isEmpty()) {
                error("Port number required")
            } else if (it.contains(notNumber)) {
                error("Invalid port number")
            } else {
                val port = it.toInt()
                if (!nodeController.isPortAvailable(port)) {
                    error("Port $it is unavailable")
                } else if (port == model.p2pPort.value) {
                    error("Clashes with P2P port")
                } else if (port == model.rpcPort.value) {
                    error("Clashes with RPC port")
                } else if (port == model.webPort.value) {
                    error("Clashes with web port")
                } else {
                    null
                }
            }
        }
    }

    /**
     * Launches a Corda node that was configured via the form.
     */
    fun launch() {
        model.commit()
        val config = nodeController.validate(model.item)
        if (config != null) {
            nodeConfigView.isVisible = false
            config.install(cordapps)
            launchNode(config)
        }
    }

    /**
     * Launches a preconfigured Corda node, e.g. from a saved profile.
     */
    fun launch(config: NodeConfig) {
        nodeController.register(config)
        launchNode(config)
    }

    private fun launchNode(config: NodeConfig) {
        nodeTab.text = config.legalName
        nodeTerminalView.open(config, onExit = { onTerminalExit(config) })

        nodeTab.setOnSelectionChanged {
            if (nodeTab.isSelected) {
                // Doesn't work yet
                nodeTerminalView.refreshTerminal()
            }
        }
    }

    private fun onTerminalExit(config: NodeConfig) {
        Platform.runLater {
            nodeTab.requestClose()
            nodeController.dispose(config)
            main.forceAtLeastOneTab()
        }
    }
}
