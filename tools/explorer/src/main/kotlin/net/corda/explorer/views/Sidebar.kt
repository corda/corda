package net.corda.explorer.views

import net.corda.client.fxutils.map
import net.corda.client.model.writableValue
import net.corda.explorer.model.SelectedView
import net.corda.explorer.model.TopLevelModel
import javafx.beans.value.WritableValue
import javafx.geometry.Pos
import javafx.scene.control.ContentDisplay
import javafx.scene.input.MouseButton
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment
import tornadofx.View
import tornadofx.button
import tornadofx.imageview

class Sidebar : View() {
    override val root: VBox by fxml()
    private val selectedView: WritableValue<SelectedView> by writableValue(TopLevelModel::selectedView)

    init {
        // TODO: Obtain views from ViewModel.
        arrayOf(SelectedView.Home, SelectedView.Cash, SelectedView.Transaction, SelectedView.NewTransaction, SelectedView.Network, SelectedView.Setting).forEach { view ->
            root.apply {
                button(view.displayableName) {
                    graphic = imageview {
                        image = view.image
                        // TODO : Use CSS instead.
                        fitWidth = 35.0
                        fitHeight = 35.0
                    }
                    styleClass.add("sidebar-menu-item")
                    setOnMouseClicked { e ->
                        if (e.button == MouseButton.PRIMARY) {
                            selectedView.value = view
                        }
                    }
                    // Transform to smaller icon layout when sidebar width is below 150.
                    val smallIconProperty = widthProperty().map { (it.toDouble() < 150) }

                    contentDisplayProperty().bind(smallIconProperty.map { if (it) ContentDisplay.TOP else ContentDisplay.LEFT })
                    textAlignmentProperty().bind(smallIconProperty.map { if (it) TextAlignment.CENTER else TextAlignment.LEFT })
                    alignmentProperty().bind(smallIconProperty.map { if (it) Pos.CENTER else Pos.CENTER_LEFT })
                    fontProperty().bind(smallIconProperty.map { if (it) Font.font(9.0) else Font.font(13.0) })
                    wrapTextProperty().bind(smallIconProperty)
                }
            }
        }
    }
}
