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
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.TitledPane
import javafx.scene.input.MouseButton
import javafx.scene.layout.TilePane
import net.corda.client.jfx.model.observableList
import net.corda.client.jfx.model.writableValue
import net.corda.client.jfx.utils.concatenate
import net.corda.client.jfx.utils.map
import net.corda.explorer.model.CordaView
import net.corda.explorer.model.CordaViewModel

class Dashboard : CordaView() {
    override val root: Parent by fxml()
    override val icon = FontAwesomeIcon.DASHBOARD
    private val tilePane: TilePane by fxid()
    private val template: TitledPane by fxid()

    private val selectedView by writableValue(CordaViewModel::selectedView)
    private val registeredViews by observableList(CordaViewModel::registeredViews)
    // This needed to be here or else it will get GCed and won't get notified.
    private val widgetPanes = registeredViews.map { getWidget(it) }.concatenate()

    init {
        Bindings.bindContent(tilePane.children, widgetPanes)
        // Dynamically change column count and width according to the window size.
        tilePane.widthProperty().addListener { _ ->
            val prefWidth = 350
            val columns: Int = ((tilePane.width - 10) / prefWidth).toInt()
            tilePane.children.forEach { (it as? TitledPane)?.prefWidth = (tilePane.width - 10) / columns }
        }
    }

    private fun getWidget(view: CordaView): ObservableList<Node> {
        return view.widgets.map {
            TitledPane(it.name, it.node).apply {
                styleClass.addAll(template.styleClass)
                collapsibleProperty().bind(template.collapsibleProperty())
                setOnMouseClicked {
                    if (it.button == MouseButton.PRIMARY) {
                        selectedView.value = view
                    }
                }
                it.icon?.let { graphic = FontAwesomeIconView(it).apply { glyphSize = 30.0 } }
            }
        }
    }
}