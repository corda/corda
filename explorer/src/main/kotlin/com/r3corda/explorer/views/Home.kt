package com.r3corda.explorer.views

import com.r3corda.client.fxutils.AmountBindings
import com.r3corda.client.model.*
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.withoutIssuer
import com.r3corda.explorer.formatters.AmountFormatter
import com.r3corda.explorer.formatters.NumberFormatter
import com.r3corda.explorer.model.SelectedView
import com.r3corda.explorer.model.SettingsModel
import com.r3corda.explorer.model.TopLevelModel
import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.beans.value.WritableValue
import javafx.collections.ObservableList
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.input.MouseButton
import javafx.scene.layout.TilePane
import org.fxmisc.easybind.EasyBind
import tornadofx.View
import java.util.*


class Home : View() {
    override val root: TilePane by fxml()

    private val ourCashPane: TitledPane by fxid("OurCashPane")
    private val ourCashLabel: Label by fxid("OurCashLabel")

    private val ourTransactionsPane: TitledPane by fxid("OurTransactionsPane")
    private val ourTransactionsLabel: Label by fxid("OurTransactionsLabel")

    private val selectedView: WritableValue<SelectedView> by writableValue(TopLevelModel::selectedView)
    private val cashStates: ObservableList<StateAndRef<Cash.State>> by observableList(ContractStateModel::cashStates)
    private val gatheredTransactionDataList: ObservableList<out GatheredTransactionData>
            by observableListReadOnly(GatheredTransactionDataModel::gatheredTransactionDataList)
    private val reportingCurrency: ObservableValue<Currency> by observableValue(SettingsModel::reportingCurrency)
    private val exchangeRate: ObservableValue<ExchangeRate> by observableValue(ExchangeRateModel::exchangeRate)

    private val sumAmount = AmountBindings.sumAmountExchange(
            EasyBind.map(cashStates) { it.state.data.amount.withoutIssuer() },
            reportingCurrency,
            exchangeRate
    )

    init {
        val formatter = AmountFormatter.currency(AmountFormatter.kmb(NumberFormatter.doubleComma))

        ourCashLabel.textProperty().bind(EasyBind.map(sumAmount) { formatter.format(it) })
        ourCashPane.setOnMouseClicked { clickEvent ->
            if (clickEvent.button == MouseButton.PRIMARY) {
                selectedView.value = SelectedView.Cash
            }
        }

        ourTransactionsLabel.textProperty().bind(
                Bindings.createStringBinding({
                    NumberFormatter.intComma.format(gatheredTransactionDataList.size)
                }, arrayOf(gatheredTransactionDataList))
        )
        ourTransactionsPane.setOnMouseClicked { clickEvent ->
            if (clickEvent.button == MouseButton.PRIMARY) {
                selectedView.value = SelectedView.Transaction
            }
        }

    }
}
