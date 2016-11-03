package com.r3corda.explorer.views

import com.r3corda.client.fxutils.map
import com.r3corda.client.model.GatheredTransactionData
import com.r3corda.client.model.GatheredTransactionDataModel
import com.r3corda.client.model.observableListReadOnly
import com.r3corda.client.model.writableValue
import com.r3corda.explorer.model.SelectedView
import com.r3corda.explorer.model.TopLevelModel
import javafx.beans.binding.Bindings
import javafx.beans.value.WritableValue
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.TilePane
import tornadofx.View
import tornadofx.find

class Home : View() {
    override val root: Parent by fxml()
    private val tilePane: TilePane by fxid()
    private val ourCashPane: TitledPane by fxid()
    private val ourTransactionsLabel: Label by fxid()

    private val selectedView: WritableValue<SelectedView> by writableValue(TopLevelModel::selectedView)
    private val gatheredTransactionDataList: ObservableList<out GatheredTransactionData>
            by observableListReadOnly(GatheredTransactionDataModel::gatheredTransactionDataList)

    init {
        // TODO: register views in view model and populate the dashboard dynamically.
        ourTransactionsLabel.textProperty().bind(
                Bindings.size(gatheredTransactionDataList).map { it.toString() }
        )

        ourCashPane.apply {
            content = find(CashViewer::class).widget
        }

        tilePane.widthProperty().addListener { e ->
            val prefWidth = 350
            val columns: Int = ((tilePane.width - 10) / prefWidth).toInt()
            tilePane.children.forEach { (it as? TitledPane)?.prefWidth = (tilePane.width - 10) / columns }
        }
    }

    fun changeView(event: MouseEvent) {
        if (event.button == MouseButton.PRIMARY) {
            selectedView.value = SelectedView.valueOf((event.source as Node).id)
        }
    }
}