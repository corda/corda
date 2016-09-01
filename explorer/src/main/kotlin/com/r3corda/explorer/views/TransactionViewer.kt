package com.r3corda.explorer.views

import com.r3corda.client.fxutils.ChosenList
import com.r3corda.client.model.*
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.withoutIssuer
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.explorer.formatters.AmountFormatter
import com.r3corda.explorer.model.ReportingCurrencyModel
import com.r3corda.explorer.ui.SingleRowSelection
import com.r3corda.explorer.ui.setColumnPrefWidthPolicy
import com.r3corda.explorer.ui.singleRowSelection
import com.r3corda.explorer.ui.toTableCellFactory
import com.r3corda.node.services.monitor.ServiceToClientEvent
import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.fxmisc.easybind.EasyBind
import tornadofx.View
import java.time.Instant
import java.util.*

class TransactionViewer: View() {
    override val root: VBox by fxml()

    // Top half (transactions table)
    private val transactionViewTable: TableView<ViewerNode> by fxid("TransactionViewTable")
    private val transactionViewTransactionId: TableColumn<ViewerNode, String> by fxid("TransactionViewTransactionId")
    private val transactionViewOriginator: TableColumn<ViewerNode, String> by fxid("TransactionViewOriginator")
    private val transactionViewTransactionStatus: TableColumn<ViewerNode, Pair<TransactionCreateStatus?, ProtocolStatus?>> by fxid("TransactionViewTransactionStatus")
    private val transactionViewStatusUpdated: TableColumn<ViewerNode, Instant> by fxid("TransactionViewStatusUpdated")
    private val transactionViewCommandTypes: TableColumn<ViewerNode, String> by fxid("TransactionViewCommandTypes")
    private val transactionViewTotalValueEquiv: TableColumn<ViewerNode, Amount<Currency>> by fxid("TransactionViewTotalValueEquiv")

    // Bottom half (details)
    private val lowLevelEventsTable: TableView<ServiceToClientEvent> by fxid("LowLevelEventsTable")
    private val lowLevelEventsTimestamp: TableColumn<ServiceToClientEvent, Instant> by fxid("LowLevelEventsTimestamp")
    private val lowLevelEventsEvent: TableColumn<ServiceToClientEvent, ServiceToClientEvent> by fxid("LowLevelEventsEvent")

    private val gatheredTransactionDataList: ObservableList<out GatheredTransactionData>
            by observableListReadOnly(GatheredTransactionDataModel::gatheredTransactionDataList)
    private val reportingExchange: ObservableValue<Pair<Currency, (Amount<Currency>) -> Amount<Currency>>>
            by observableValue(ReportingCurrencyModel::reportingExchange)

    data class ViewerNode(
            val transactionId: ObservableValue<Pair<Long?, UUID?>>,
            val originator: ObservableValue<String>,
            val transactionStatus: ObservableValue<Pair<TransactionCreateStatus?, ProtocolStatus?>>,
            val statusUpdated: ObservableValue<Instant>,
            val commandTypes: ObservableValue<Collection<Class<CommandData>>>,
            val viewTotalValueEquiv: ObservableValue<Amount<Currency>?>,
            val transaction: ObservableValue<SignedTransaction?>,
            val allEvents: ObservableList<out ServiceToClientEvent>
    )

    private val viewerNodes = EasyBind.map(gatheredTransactionDataList) {
        ViewerNode(
                transactionId = EasyBind.combine(it.fiberId, it.uuid) { fiberId, uuid -> Pair(fiberId, uuid) },
                originator = EasyBind.map(it.uuid) { uuid ->
                    if (uuid == null) {
                        "Someone"
                    } else {
                        "Us"
                    }
                },
                transactionStatus = EasyBind.combine(it.status, it.protocolStatus) { status, protocolStatus ->
                    Pair(status, protocolStatus)
                },
                statusUpdated = it.lastUpdate,
                commandTypes = EasyBind.map(it.transaction) {
                    val commands = mutableSetOf<Class<CommandData>>()
                    it?.tx?.commands?.forEach {
                        commands.add(it.value.javaClass)
                    }
                    commands
                },
                viewTotalValueEquiv = EasyBind.combine(reportingExchange, it.transaction) { exchange, transaction ->
                    transaction?.let { calculateTotalEquiv(exchange.first, exchange.second, transaction) }
                },
                transaction = it.transaction,
                allEvents = it.allEvents
        )
    }

    private fun calculateTotalEquiv(
            reportingCurrency: Currency,
            exchange: (Amount<Currency>) -> Amount<Currency>,
            transaction: SignedTransaction): Amount<Currency> {
        return transaction.tx.outputs.map { it.data }.filterIsInstance<Cash.State>().fold(
                initial = Amount(0, reportingCurrency),
                operation = { sum, cashState -> sum + exchange(cashState.amount.withoutIssuer()) }
        )
    }

    private val selectedViewerNode = transactionViewTable.singleRowSelection()

    private val noLowLevelEvents = FXCollections.emptyObservableList<ServiceToClientEvent>()
    private val lowLevelEvents = ChosenList(EasyBind.map(selectedViewerNode) {
        when (it) {
            is SingleRowSelection.None -> noLowLevelEvents
            is SingleRowSelection.Selected -> it.node.allEvents
        }
    })

    init {
        // Transaction table
        Bindings.bindContent(transactionViewTable.items, viewerNodes)

        transactionViewTable.setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, column ->
            Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / transactionViewTable.columns.size).toInt()
        }

        transactionViewTransactionId.setCellValueFactory {
            EasyBind.map(it.value.transactionId) {
                val (fiberId, uuid) = it
                if (fiberId == null && uuid == null) {
                    "???"
                } else {
                    (uuid?.toString() ?: "") + (fiberId?.let { "[$it]" } ?: "")
                }
            }
        }
        transactionViewOriginator.setCellValueFactory { it.value.originator }
        transactionViewTransactionStatus.setCellValueFactory { it.value.transactionStatus }
        transactionViewTransactionStatus.setCellFactory {
            object : TableCell<ViewerNode, Pair<TransactionCreateStatus?, ProtocolStatus?>>() {
                val label = Label()
                override fun updateItem(
                        value: Pair<TransactionCreateStatus?, ProtocolStatus?>?,
                        empty: Boolean
                ) {
                    super.updateItem(value, empty)
                    if (value == null || empty) {
                        graphic = null
                        text = null
                    } else {
                        graphic = label
                        val backgroundFill = when (value.first) {
                            is TransactionCreateStatus.Started -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
                            is TransactionCreateStatus.Failed -> BackgroundFill(Color.SALMON, CornerRadii.EMPTY, Insets.EMPTY)
                            null -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
                        }
                        label.background = Background(backgroundFill)
                        label.text = if (value.first == null && value.second == null){
                            "???"
                        } else {
                            (value.first?.toString() ?: "") + (value.second?.let { "[${it.toString()}]" } ?: "")
                        }
                    }
                }
            }
        }

        transactionViewStatusUpdated.setCellValueFactory { it.value.statusUpdated }
        transactionViewCommandTypes.setCellValueFactory {
            EasyBind.map(it.value.commandTypes) { it.map { it.simpleName }.joinToString(",") }
        }
        transactionViewTotalValueEquiv.setCellValueFactory<ViewerNode, Amount<Currency>> { it.value.viewTotalValueEquiv }
        transactionViewTotalValueEquiv.cellFactory = AmountFormatter.comma.toTableCellFactory()

        // Low level events
        Bindings.bindContent(lowLevelEventsTable.items, lowLevelEvents)
        lowLevelEventsTimestamp.setCellValueFactory { ReadOnlyObjectWrapper(it.value.time) }
        lowLevelEventsEvent.setCellValueFactory { ReadOnlyObjectWrapper(it.value) }
        lowLevelEventsTable.setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, column ->
            Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / lowLevelEventsTable.columns.size).toInt()
        }
    }
}
