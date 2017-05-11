package net.corda.demobench.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconFactory
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import javafx.util.StringConverter
import net.corda.core.div
import net.corda.core.exists
import net.corda.core.node.CityDatabase
import net.corda.core.node.PhysicalLocation
import net.corda.core.readAllLines
import net.corda.core.utilities.normaliseLegalName
import net.corda.core.utilities.validateLegalName
import net.corda.core.writeLines
import net.corda.demobench.model.*
import net.corda.demobench.ui.CloseableTab
import org.bouncycastle.asn1.x500.style.RFC4519Style.name
import org.controlsfx.control.CheckListView
import tornadofx.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class NodeTabView : Fragment() {
    override val root = stackpane {}

    private val main by inject<DemoBenchView>()
    private val showConfig by param(true)

    private companion object : Component() {
        const val textWidth = 465.0

        val jvm by inject<JVMConfig>()
        val cordappPathsFile = jvm.dataHome / "cordapp-paths.txt"

        fun loadDefaultCordappPaths(): MutableList<Path> {
            if (cordappPathsFile.exists())
                return cordappPathsFile.readAllLines().map { Paths.get(it) }.filter { it.exists() }.toMutableList()
            else
                return ArrayList()
        }

        // This is shared between tabs.
        private val cordapps = loadDefaultCordappPaths().observable()

        init {
            // Save when the list is changed.
            cordapps.addListener(InvalidationListener {
                log.info("Writing cordapp paths to $cordappPathsFile")
                cordappPathsFile.writeLines(cordapps.map { it.toAbsolutePath().toString() })
            })
        }
    }

    private val nodeController by inject<NodeController>()
    private val serviceController by inject<ServiceController>()
    private val chooser = FileChooser()

    private val model = NodeDataModel()
    private val availableServices: List<String> = if (nodeController.hasNetworkMap()) serviceController.services else serviceController.notaries

    private val nodeTerminalView = find<NodeTerminalView>()
    private val nodeConfigView = stackpane {
        isVisible = showConfig
        styleClass += "config-view"

        form {
            fieldset("Configuration") {
                isFillWidth = false

                field("Legal name") { nodeNameField() }
                field("Nearest city") { nearestCityField() }
            }

            hbox {
                styleClass.addAll("node-panel")
                vboxConstraints { vGrow = Priority.ALWAYS }

                fieldset("CorDapps") {
                    hboxConstraints { hGrow = Priority.ALWAYS }
                    styleClass.addAll("cordapps-panel")

                    listview(cordapps) {
                        vboxConstraints { vGrow = Priority.ALWAYS }
                        setOnKeyPressed { key ->
                            if ((key.code == KeyCode.DELETE) && !selectionModel.isEmpty) {
                                cordapps.remove(selectionModel.selectedItem)
                            }
                            key.consume()
                        }
                        cellCache { item ->
                            hbox {
                                label(item.fileName.toString())
                                pane {
                                    hboxConstraints { hgrow = Priority.ALWAYS }
                                }
                                val delete = FontAwesomeIconView(FontAwesomeIcon.MINUS_CIRCLE)
                                delete.setOnMouseClicked {
                                    cordapps.remove(selectionModel.selectedItem)
                                }
                                delete.style += "; -fx-cursor: hand"
                                addChildIfPossible(delete)
                            }
                        }
                    }
                }

                fieldset("Services") {
                    styleClass.addAll("services-panel")

                    val servicesList = CheckListView(availableServices.observable()).apply {
                        vboxConstraints { vGrow = Priority.ALWAYS }
                        model.item.extraServices.set(checkModel.checkedItems)
                        if (!nodeController.hasNetworkMap()) {
                            checkModel.check(0)
                        }
                    }
                    add(servicesList)
                }
            }

            hbox {
                button("Add CorDapp") {
                    setOnAction {
                        val app = (chooser.showOpenDialog(null) ?: return@setOnAction).toPath()
                        if (!cordapps.contains(app)) {
                            cordapps.add(app)
                        }
                    }

                    FontAwesomeIconFactory.get().setIcon(this, FontAwesomeIcon.PLUS)
                }

                // Spacer pane.
                pane {
                    hboxConstraints { hGrow = Priority.ALWAYS }
                }

                button("Start node") {
                    styleClass += "start-button"
                    setOnAction {
                        if (model.validate()) {
                            launch()
                            main.enableAddNodes()
                            main.enableSaveProfile()
                        }
                    }
                    graphic = FontAwesomeIconView(FontAwesomeIcon.PLAY_CIRCLE).apply { style += "-fx-fill: white" }
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
        nodeTab.graphic = FontAwesomeIconView(FontAwesomeIcon.BANK)

        root.add(nodeConfigView)
        root.add(nodeTerminalView)

        model.p2pPort.value = nodeController.nextPort
        model.rpcPort.value = nodeController.nextPort
        model.webPort.value = nodeController.nextPort
        model.h2Port.value = nodeController.nextPort

        val defaults = SuggestedDetails.nextBank
        model.legalName.value = defaults.first
        model.nearestCity.value = CityDatabase[defaults.second]

        chooser.title = "CorDapps"
        chooser.initialDirectory = jvm.dataHome.toFile()
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("CorDapps (*.jar)", "*.jar", "*.JAR"))

        model.validate(focusFirstError = true)
    }

    private fun Pane.nodeNameField() = textfield(model.legalName) {
        minWidth = textWidth
        validator {
            if (it == null) {
                error("Node name is required")
            } else if (nodeController.nameExists(normaliseLegalName(it))) {
                error("Node with this name already exists")
            } else {
                try {
                    validateLegalName(normaliseLegalName(it))
                    null
                } catch (e: IllegalArgumentException) {
                    error(e.message)
                }
            }
        }
    }

    private val flags = runAsync {
        CityDatabase.cityMap.values.map { it.countryCode }.toSet().map { it to Image(resources["/net/corda/demobench/flags/$it.png"]) }.toMap()
    }

    private fun Pane.nearestCityField(): ComboBox<PhysicalLocation> {
        return combobox(model.nearestCity, CityDatabase.cityMap.values.toList().sortedBy { it.description }) {
            minWidth = textWidth
            styleClass += "city-picker"
            cellFormat {
                graphic = hbox(spacing = 10) {
                    imageview {
                        image = flags.get()[it.countryCode]
                    }
                    label(it.description)
                    alignment = Pos.CENTER_LEFT
                }
            }

            validator {
                if (it == null) error("Please select a city") else null
            }

            converter = object : StringConverter<PhysicalLocation>() {
                override fun toString(loc: PhysicalLocation?) = loc?.description ?: ""
                override fun fromString(string: String): PhysicalLocation? = CityDatabase[string]
            }

            value = CityDatabase["London"]

            isEditable = true
            makeAutocompletable()
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
        val countryCode = CityDatabase.cityMap[config.nearestCity ?: "Nowhere"]?.countryCode
        if (countryCode != null) {
            nodeTab.graphic = ImageView(flags.get()[countryCode]).apply { fitWidth = 24.0; isPreserveRatio = true }
        }
        nodeTab.text = config.legalName.toString()
        nodeTerminalView.open(config) { exitCode ->
            Platform.runLater {
                if (exitCode == 0)
                    nodeTab.requestClose()
                nodeController.dispose(config)
                main.forceAtLeastOneTab()
            }
        }

        nodeTab.setOnSelectionChanged {
            if (nodeTab.isSelected) {
                nodeTerminalView.takeFocus()
            }
        }
    }
}
