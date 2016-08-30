package com.r3corda.explorer.views

import com.r3corda.client.model.objectProperty
import com.r3corda.explorer.model.SelectedView
import com.r3corda.explorer.model.TopLevelModel
import javafx.beans.property.ObjectProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.fxmisc.easybind.EasyBind
import tornadofx.View

class TopLevel : View() {
    override val root: VBox by fxml()
    val selection: BorderPane by fxid("SelectionBorderPane")

    private val header: Header by inject()
    private val home: Home by inject()
    private val cash: CashViewer by inject()
    private val transaction: TransactionViewer by inject()

    // Note: this is weirdly very important, as it forces the initialisation of Views. Therefore this is the entry
    // point to the top level observable/stream wiring! Any events sent before this init may be lost!
    private val homeRoot = home.root
    private val cashRoot = cash.root
    private val transactionRoot = transaction.root

    private fun getView(selection: SelectedView) = when (selection) {
        SelectedView.Home -> homeRoot
        SelectedView.Cash -> cashRoot
        SelectedView.Transaction -> transactionRoot
    }
    val selectedView: ObjectProperty<SelectedView> by objectProperty(TopLevelModel::selectedView)

    init {
        VBox.setVgrow(selection, Priority.ALWAYS)
        selection.centerProperty().bind(EasyBind.map(selectedView) { getView(it) })

        primaryStage.addEventHandler(KeyEvent.KEY_RELEASED) { keyEvent ->
            if (keyEvent.code == KeyCode.ESCAPE) {
                selectedView.value = SelectedView.Home
            }
        }

        root.children.add(0, header.root)
    }
}
