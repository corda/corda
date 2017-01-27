package net.corda.demobench.views

import java.text.DecimalFormat
import javafx.scene.control.SelectionMode.MULTIPLE
import javafx.util.converter.NumberStringConverter
import net.corda.demobench.model.NodeController
import net.corda.demobench.model.NodeDataModel
import net.corda.demobench.model.ServiceController
import net.corda.demobench.ui.CloseableTab
import tornadofx.*

class NodeTabView : Fragment() {
    override val root = stackpane {}

    private val main by inject<DemoBenchView>()

    private val INTEGER_FORMAT = DecimalFormat()
    private val NOT_NUMBER = "[^\\d]".toRegex()

    private val model = NodeDataModel()
    private val nodeController by inject<NodeController>()
    private val serviceController by inject<ServiceController>()

    private val nodeTerminalView = find<NodeTerminalView>()
    private val nodeConfigView = stackpane {
        form {
            fieldset("Configuration") {
                field("Node Name") {
                    textfield(model.legalName) {
                        minWidth = 200.0
                        maxWidth = 200.0
                        validator {
                            if (it == null) {
                                error("Node name is required")
                            } else {
                                val name = it.trim()
                                if (name.isEmpty()) {
                                    error("Node name is required")
                                } else if (nodeController.nameExists(name)) {
                                    error("Node with this name already exists")
                                } else if (name.length > 10) {
                                    error("Name is too long")
                                } else {
                                    null
                                }
                            }
                        }
                    }
                }
                field("Nearest City") {
                    textfield(model.nearestCity) {
                        minWidth = 200.0
                        maxWidth = 200.0
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
                }
                field("P2P Port") {
                    textfield(model.artemisPort, NumberStringConverter(INTEGER_FORMAT)) {
                        minWidth = 100.0
                        maxWidth = 100.0
                        validator {
                            if ((it == null) || it.isEmpty()) {
                                error("Port number required")
                            } else if (it.contains(NOT_NUMBER)) {
                                error("Invalid port number")
                            } else {
                                null
                            }
                        }
                    }
                }
                field("Web Port") {
                    textfield(model.webPort, NumberStringConverter(INTEGER_FORMAT)) {
                        minWidth = 100.0
                        maxWidth = 100.0
                        validator {
                            if ((it == null) || it.isEmpty()) {
                                error("Port number required")
                            } else if (it.contains(NOT_NUMBER)) {
                                error("Invalid port number")
                            } else {
                                null
                            }
                        }
                    }
                }
            }

            fieldset("Services") {
                listview(serviceController.services.observable()) {
                    selectionModel.selectionMode = MULTIPLE
                    model.item.extraServices.set(selectionModel.selectedItems)
                }
            }

            button("Create Node") {
                setOnAction() {
                    if (model.validate()) {
                        launch()
                        main.enableAddNodes()
                    }
                }
            }
        }
    }

    val nodeTab = CloseableTab("New Node", root)

    fun launch() {
        model.commit()
        val config = nodeController.validate(model.item)
        if (config != null) {
            nodeConfigView.isVisible = false
            nodeTab.text = config.legalName
            nodeTerminalView.open(config)

            nodeTab.setOnSelectionChanged {
                if (nodeTab.isSelected) {
                    // Doesn't work yet
                    nodeTerminalView.refreshTerminal()
                }
            }
        }
    }

    init {
        INTEGER_FORMAT.isGroupingUsed = false

        // Ensure that we close the terminal along with the tab.
        nodeTab.setOnCloseRequest {
            nodeTerminalView.close()
        }

        root.add(nodeConfigView)
        root.add(nodeTerminalView)

        model.artemisPort.value = nodeController.nextPort
        model.webPort.value = nodeController.nextPort
    }
}
