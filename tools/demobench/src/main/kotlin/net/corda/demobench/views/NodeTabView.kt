/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconFactory
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.collections.ListChangeListener
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import javafx.util.StringConverter
import net.corda.core.internal.*
import net.corda.demobench.model.*
import net.corda.demobench.ui.CloseableTab
import net.corda.finance.CHF
import net.corda.finance.EUR
import net.corda.finance.GBP
import net.corda.finance.USD
import net.corda.finance.utils.CityDatabase
import net.corda.finance.utils.WorldMapLocation
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
        val cordappPathsFile: Path = jvm.dataHome / "cordapp-paths.txt"

        fun loadDefaultCordappPaths(): MutableList<Path> {
            return if (cordappPathsFile.exists())
                cordappPathsFile.readAllLines().map { Paths.get(it) }.filter { it.exists() }.toMutableList()
            else
                ArrayList()
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
    private val chooser = FileChooser()

    private val model = NodeDataModel()

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

                fieldset("Additional configuration") {
                    styleClass.addAll("services-panel")
                    val extraServices = if (nodeController.hasNotary()) {
                        listOf(USD, GBP, CHF, EUR).map { CurrencyIssuer(it) }
                    } else {
                        listOf(NotaryService(true), NotaryService(false))
                    }

                    val servicesList = CheckListView(extraServices.observable()).apply {
                        vboxConstraints { vGrow = Priority.ALWAYS }
                        model.item.extraServices.set(checkModel.checkedItems)
                        if (!nodeController.hasNotary()) {
                            checkModel.check(0)
                            checkModel.checkedItems.addListener(ListChangeListener { change ->
                                while (change.next()) {
                                    if (change.wasAdded()) {
                                        val item = change.addedSubList.last()
                                        val idx = checkModel.getItemIndex(item)
                                        checkModel.checkedIndices.forEach {
                                            if (it != idx) checkModel.clearCheck(it)
                                        }
                                    }
                                }
                            })
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
        model.rpcAdminPort.value = nodeController.nextPort
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
        validator { rawName ->
            val normalizedName: String? = rawName?.let(LegalNameValidator::normalize)
            if (normalizedName == null) {
                error("Node name is required")
            } else if (nodeController.nameExists(normalizedName)) {
                error("Node with this name already exists")
            } else {
                try {
                    LegalNameValidator.validateOrganization(normalizedName, LegalNameValidator.Validation.MINIMAL)
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

    private fun Pane.nearestCityField(): ComboBox<WorldMapLocation> {
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

            converter = object : StringConverter<WorldMapLocation>() {
                override fun toString(loc: WorldMapLocation?) = loc?.description ?: ""
                override fun fromString(string: String): WorldMapLocation? = CityDatabase[string]
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
    fun launch(config: NodeConfigWrapper) {
        nodeController.register(config)
        launchNode(config)
    }

    private fun launchNode(config: NodeConfigWrapper) {
        val countryCode = CityDatabase.cityMap[config.nodeConfig.myLegalName.locality]?.countryCode
        if (countryCode != null) {
            nodeTab.graphic = ImageView(flags.get()[countryCode]).apply { fitWidth = 24.0; isPreserveRatio = true }
        }
        nodeTab.text = config.nodeConfig.myLegalName.organisation
        nodeTerminalView.open(config) { exitCode ->
            Platform.runLater {
                if (exitCode == 0) {
                    nodeTab.requestClose()
                } else {
                    // The node did not shut down cleanly. Keep the
                    // terminal open but ensure that it is disabled.
                    nodeTerminalView.shutdown()
                }
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
