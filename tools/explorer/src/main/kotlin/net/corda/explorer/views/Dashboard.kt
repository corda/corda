package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.binding.Bindings
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.TitledPane
import javafx.scene.input.MouseButton
import javafx.scene.layout.TilePane
import net.corda.client.fxutils.concatenate
import net.corda.client.fxutils.map
import net.corda.client.model.observableList
import net.corda.client.model.writableValue
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
        tilePane.widthProperty().addListener { e ->
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
            }
        }
    }

}