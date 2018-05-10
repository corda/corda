/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment
import javafx.stage.Stage
import javafx.stage.WindowEvent
import net.corda.client.jfx.model.*
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.map
import net.corda.explorer.formatters.PartyNameFormatter
import net.corda.explorer.model.CordaViewModel
import tornadofx.*

/**
 * The root view embeds the [Shell] and provides support for the status bar, and modal dialogs.
 */
class MainView : View(WINDOW_TITLE) {
    override val root by fxml<Parent>()

    // Inject components.
    private val userButton by fxid<MenuButton>()
    private val exit by fxid<MenuItem>()
    private val sidebar by fxid<VBox>()
    private val selectionBorderPane by fxid<BorderPane>()
    private val mainSplitPane by fxid<SplitPane>()
    private val rpcWarnLabel by fxid<Label>()

    // Inject data.
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val selectedView by objectProperty(CordaViewModel::selectedView)
    private val registeredViews by observableList(CordaViewModel::registeredViews)
    private val proxy by observableValue(NodeMonitorModel::proxyObservable)

    private val menuItemCSS = "sidebar-menu-item"
    private val menuItemArrowCSS = "sidebar-menu-item-arrow"
    private val menuItemSelectedCSS = "$menuItemCSS-selected"

    fun initializeControls() {
        // Header
        userButton.textProperty().bind(myIdentity.map { it?.let { PartyNameFormatter.short.format(it.name) } })
        exit.setOnAction {
            (root.scene.window as Stage).fireEvent(WindowEvent(root.scene.window, WindowEvent.WINDOW_CLOSE_REQUEST))
        }
        // Sidebar
        val menuItems = registeredViews.map {
            // This needed to be declared val or else it will get GCed and listener unregistered.
            val buttonStyle = ChosenList(selectedView.map { selected ->
                if (selected == it) listOf(menuItemCSS, menuItemSelectedCSS).observable() else listOf(menuItemCSS).observable()
            }, "buttonStyle")
            stackpane {
                button(it.title) {
                    graphic = FontAwesomeIconView(it.icon).apply {
                        glyphSize = 30
                        textAlignment = TextAlignment.CENTER
                        fillProperty().bind(this@button.textFillProperty())
                    }
                    Bindings.bindContent(styleClass, buttonStyle)
                    setOnMouseClicked { e ->
                        if (e.button == MouseButton.PRIMARY) {
                            selectedView.value = it
                        }
                    }
                    // Transform to smaller icon layout when sidebar width is below 150.
                    val smallIconProperty = widthProperty().map { (it.toDouble() < 150) }
                    contentDisplayProperty().bind(smallIconProperty.map { if (it) ContentDisplay.TOP else ContentDisplay.LEFT })
                    textAlignmentProperty().bind(smallIconProperty.map { if (it) TextAlignment.CENTER else TextAlignment.LEFT })
                    alignmentProperty().bind(smallIconProperty.map { if (it) Pos.CENTER else Pos.CENTER_LEFT })
                    fontProperty().bind(smallIconProperty.map { if (it) Font.font(10.0) else Font.font(12.0) })
                    wrapTextProperty().bind(smallIconProperty)
                }
                // Small triangle indicator to make selected view more obvious.
                add(FontAwesomeIconView(FontAwesomeIcon.CARET_LEFT).apply {
                    StackPane.setAlignment(this, Pos.CENTER_RIGHT)
                    StackPane.setMargin(this, Insets(0.0, -5.0, 0.0, 0.0))
                    styleClass.add(menuItemArrowCSS)
                    visibleProperty().bind(selectedView.map { selected -> selected == it })
                })
            }
        }
        Bindings.bindContent(sidebar.children, menuItems)
        // Main view
        selectionBorderPane.centerProperty().bind(selectedView.map { it?.root })
        // Trigger depending on RPC connectivity status.
        val proxyNotAvailable = proxy.map { it == null }
        mainSplitPane.disableProperty().bind(proxyNotAvailable)
        rpcWarnLabel.visibleProperty().bind(proxyNotAvailable)
    }
}