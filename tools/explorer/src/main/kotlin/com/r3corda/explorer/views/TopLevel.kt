package com.r3corda.explorer.views

import com.r3corda.client.fxutils.map
import com.r3corda.client.model.objectProperty
import com.r3corda.explorer.model.SelectedView
import com.r3corda.explorer.model.TopLevelModel
import javafx.beans.property.ObjectProperty
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.text.TextAlignment
import tornadofx.View
import tornadofx.add
import tornadofx.gridpane
import tornadofx.label

class TopLevel : View() {
    override val root: Parent by fxml()
    val selectionBorderPane: BorderPane by fxid()
    val sidebarPane: Pane by fxid()

    private val header: Header by inject()
    private val sidebar: Sidebar by inject()
    private val home: Home by inject()
    private val cash: CashViewer by inject()
    private val transaction: TransactionViewer by inject()
    private val newTransaction: NewTransaction by inject()

    // Note: this is weirdly very important, as it forces the initialisation of Views. Therefore this is the entry
    // point to the top level observable/stream wiring! Any events sent before this init may be lost!
    private val homeRoot = home.root
    private val cashRoot = cash.root
    private val transactionRoot = transaction.root
    private val newTransactionRoot = newTransaction.root

    val selectedView: ObjectProperty<SelectedView> by objectProperty(TopLevelModel::selectedView)

    init {
        selectionBorderPane.centerProperty().bind(selectedView.map {
            when (it) {
                SelectedView.Home -> homeRoot
                SelectedView.Cash -> cashRoot
                SelectedView.Transaction -> transactionRoot
                SelectedView.NewTransaction -> newTransactionRoot
                else -> gridpane {
                    label("Under Construction...") {
                        maxWidth = Double.MAX_VALUE
                        textAlignment = TextAlignment.CENTER
                        alignment = Pos.CENTER
                        GridPane.setVgrow(this, Priority.ALWAYS)
                        GridPane.setHgrow(this, Priority.ALWAYS)
                    }
                }
            }
        })
        selectionBorderPane.center.styleClass.add("no-padding")
        sidebarPane.add(sidebar.root)
        selectionBorderPane.top = header.root
    }
}