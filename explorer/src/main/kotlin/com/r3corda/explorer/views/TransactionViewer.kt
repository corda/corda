package com.r3corda.explorer.views

import com.r3corda.client.fxutils.ChosenList
import com.r3corda.client.model.*
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.withoutIssuer
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.transactions.LedgerTransaction
import com.r3corda.explorer.AmountDiff
import com.r3corda.explorer.formatters.AmountFormatter
import com.r3corda.explorer.formatters.Formatter
import com.r3corda.explorer.formatters.NumberFormatter
import com.r3corda.explorer.model.IdentityModel
import com.r3corda.explorer.model.ReportingCurrencyModel
import com.r3corda.explorer.sign
import com.r3corda.explorer.ui.*
import com.r3corda.node.services.monitor.ServiceToClientEvent
import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.fxmisc.easybind.EasyBind
import tornadofx.View
import java.security.PublicKey
import java.time.Instant
import java.util.*

class TransactionViewer: View() {
    override val root: VBox by fxml()

    val topSplitPane: SplitPane by fxid()

    // Top half (transactions table)
    private val transactionViewTable: TableView<ViewerNode> by fxid()
    private val transactionViewFiberId: TableColumn<ViewerNode, String> by fxid()
    private val transactionViewClientUuid: TableColumn<ViewerNode, String> by fxid()
    private val transactionViewTransactionStatus: TableColumn<ViewerNode, TransactionCreateStatus?> by fxid()
    private val transactionViewProtocolStatus: TableColumn<ViewerNode, String> by fxid()
    private val transactionViewStateMachineStatus: TableColumn<ViewerNode, StateMachineStatus?> by fxid()
    private val transactionViewCommandTypes: TableColumn<ViewerNode, String> by fxid()
    private val transactionViewTotalValueEquiv: TableColumn<ViewerNode, AmountDiff<Currency>> by fxid()

    // Bottom half (details)
    private val contractStatesTitledPane: TitledPane by fxid()

    private val contractStatesInputsCountLabel: Label by fxid()
    private val contractStatesInputStatesTable: TableView<StateNode> by fxid()
    private val contractStatesInputStatesId: TableColumn<StateNode, String> by fxid()
    private val contractStatesInputStatesType: TableColumn<StateNode, Class<out ContractState>> by fxid()
    private val contractStatesInputStatesOwner: TableColumn<StateNode, String> by fxid()
    private val contractStatesInputStatesLocalCurrency: TableColumn<StateNode, Currency?> by fxid()
    private val contractStatesInputStatesAmount: TableColumn<StateNode, Long> by fxid()
    private val contractStatesInputStatesEquiv: TableColumn<StateNode, Amount<Currency>> by fxid()

    private val contractStatesOutputsCountLabel: Label by fxid()
    private val contractStatesOutputStatesTable: TableView<StateNode> by fxid()
    private val contractStatesOutputStatesId: TableColumn<StateNode, String> by fxid()
    private val contractStatesOutputStatesType: TableColumn<StateNode, Class<out ContractState>> by fxid()
    private val contractStatesOutputStatesOwner: TableColumn<StateNode, String> by fxid()
    private val contractStatesOutputStatesLocalCurrency: TableColumn<StateNode, Currency?> by fxid()
    private val contractStatesOutputStatesAmount: TableColumn<StateNode, Long> by fxid()
    private val contractStatesOutputStatesEquiv: TableColumn<StateNode, Amount<Currency>> by fxid()

    private val signaturesTitledPane: TitledPane by fxid()
    private val signaturesList: ListView<PublicKey> by fxid()

    private val lowLevelEventsTitledPane: TitledPane by fxid()
    private val lowLevelEventsTable: TableView<ServiceToClientEvent> by fxid()
    private val lowLevelEventsTimestamp: TableColumn<ServiceToClientEvent, Instant> by fxid()
    private val lowLevelEventsEvent: TableColumn<ServiceToClientEvent, ServiceToClientEvent> by fxid()

    private val matchingTransactionsLabel: Label by fxid()

    private val gatheredTransactionDataList: ObservableList<out GatheredTransactionData>
            by observableListReadOnly(GatheredTransactionDataModel::gatheredTransactionDataList)
    private val reportingExchange: ObservableValue<Pair<Currency, (Amount<Currency>) -> Amount<Currency>>>
            by observableValue(ReportingCurrencyModel::reportingExchange)

    private val myIdentity: ObservableValue<Party> by observableValue(IdentityModel::myIdentity)

    data class ViewerNode(
            val transactionId: ObservableValue<Pair<Long?, UUID?>>,
            val originator: ObservableValue<String>,
            val transactionStatus: ObservableValue<TransactionCreateStatus?>,
            val stateMachineStatus: ObservableValue<StateMachineStatus?>,
            val protocolStatus: ObservableValue<ProtocolStatus?>,
            val statusUpdated: ObservableValue<Instant>,
            val commandTypes: ObservableValue<Collection<Class<CommandData>>>,
            val totalValueEquiv: ObservableValue<AmountDiff<Currency>?>,
            val transaction: ObservableValue<LedgerTransaction?>,
            val allEvents: ObservableList<out ServiceToClientEvent>
    )

    data class StateNode(
            val transactionState: TransactionState<*>,
            val stateRef: StateRef
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
                transactionStatus = it.status,
                protocolStatus = it.protocolStatus,
                stateMachineStatus = it.stateMachineStatus,
                statusUpdated = it.lastUpdate,
                commandTypes = EasyBind.map(it.transaction) {
                    val commands = mutableSetOf<Class<CommandData>>()
                    it?.commands?.forEach {
                        commands.add(it.value.javaClass)
                    }
                    commands
                },
                totalValueEquiv = EasyBind.combine(myIdentity, reportingExchange, it.transaction) { identity, exchange, transaction ->
                    transaction?.let { calculateTotalEquiv(setOf(identity.owningKey), exchange.first, exchange.second, transaction) }
                },
                transaction = it.transaction,
                allEvents = it.allEvents
        )
    }

    private fun calculateTotalEquiv(
            relevantPublicKeys: Set<PublicKey>,
            reportingCurrency: Currency,
            exchange: (Amount<Currency>) -> Amount<Currency>,
            transaction: LedgerTransaction): AmountDiff<Currency> {
        var sum = 0L
        transaction.inputs.forEach {
            val contractState = it.state.data
            if (contractState is Cash.State && relevantPublicKeys.contains(contractState.owner)) {
                sum -= exchange(contractState.amount.withoutIssuer()).quantity
            }
        }
        transaction.outputs.forEach {
            val contractState = it.data
            if (contractState is Cash.State && relevantPublicKeys.contains(contractState.owner)) {
                sum += exchange(contractState.amount.withoutIssuer()).quantity
            }
        }
        return AmountDiff.fromLong(sum, reportingCurrency)
    }

    private val selectedViewerNode = transactionViewTable.singleRowSelection()
    private val selectedTransaction = EasyBind.monadic(selectedViewerNode).flatMap<LedgerTransaction?, SingleRowSelection<ViewerNode>> {
        when (it) {
            is SingleRowSelection.None -> ReadOnlyObjectWrapper(null)
            is SingleRowSelection.Selected -> it.node.transaction
        }
    }

    private val inputStateNodes = ChosenList<StateNode>(EasyBind.map(selectedTransaction) {
        if (it == null) {
            FXCollections.emptyObservableList<StateNode>()
        } else {
            FXCollections.observableArrayList(it.inputs.map { StateNode(it.state, it.ref) })
        }
    })

    private val outputStateNodes = ChosenList<StateNode>(EasyBind.map(selectedTransaction) {
        if (it == null) {
            FXCollections.emptyObservableList<StateNode>()
        } else {
            FXCollections.observableArrayList(it.outputs.mapIndexed { index, transactionState ->
                StateNode(transactionState, StateRef(it.id, index))
            })
        }
    })

    private val signatures = ChosenList<PublicKey>(EasyBind.map(selectedTransaction) {
        if (it == null) {
            FXCollections.emptyObservableList<PublicKey>()
        } else {
            FXCollections.observableArrayList(it.mustSign)
        }
    })


    private val noLowLevelEvents = FXCollections.emptyObservableList<ServiceToClientEvent>()
    private val lowLevelEvents = ChosenList(EasyBind.map(selectedViewerNode) {
        when (it) {
            is SingleRowSelection.None -> noLowLevelEvents
            is SingleRowSelection.Selected -> it.node.allEvents
        }
    })

    private val allNodesShown = FXCollections.observableArrayList<Node>(
            transactionViewTable,
            contractStatesTitledPane,
            signaturesTitledPane,
            lowLevelEventsTitledPane
    )
    private val onlyTransactionsTableShown = FXCollections.observableArrayList<Node>(
            transactionViewTable
    )
    private val topSplitPaneNodesShown = ChosenList<Node>(
            EasyBind.map(selectedViewerNode) { selection ->
                if (selection is SingleRowSelection.None<*>) {
                    onlyTransactionsTableShown
                } else {
                    allNodesShown
                }
            })

    private fun wireUpStatesTable(
            states: ObservableList<StateNode>,
            statesCountLabel: Label,
            statesTable: TableView<StateNode>,
            statesId: TableColumn<StateNode, String>,
            statesType: TableColumn<StateNode, Class<out ContractState>>,
            statesOwner: TableColumn<StateNode, String>,
            statesLocalCurrency: TableColumn<StateNode, Currency?>,
            statesAmount: TableColumn<StateNode, Long>,
            statesEquiv: TableColumn<StateNode, Amount<Currency>>
    ) {
        statesCountLabel.textProperty().bind(EasyBind.map(Bindings.size(states)) { "$it" })

        Bindings.bindContent(statesTable.items, states)

        statesId.setCellValueFactory { ReadOnlyObjectWrapper(it.value.stateRef.toString()) }
        statesType.setCellValueFactory { ReadOnlyObjectWrapper(it.value.transactionState.data.javaClass) }
        statesOwner.setCellValueFactory {
            val state = it.value.transactionState.data
            if (state is OwnableState) {
                ReadOnlyObjectWrapper(state.owner.toStringShort())
            } else {
                ReadOnlyObjectWrapper("???")
            }
        }
        statesLocalCurrency.setCellValueFactory {
            val state = it.value.transactionState.data
            if (state is Cash.State) {
                ReadOnlyObjectWrapper(state.amount.token.product)
            } else {
                ReadOnlyObjectWrapper(null)
            }
        }
        statesAmount.setCellValueFactory {
            val state = it.value.transactionState.data
            if (state is Cash.State) {
                ReadOnlyObjectWrapper(state.amount.quantity)
            } else {
                ReadOnlyObjectWrapper(null)
            }
        }
        statesAmount.setCellFactory(NumberFormatter.longComma.toTableCellFactory())
        statesEquiv.setCellValueFactory {
            val state = it.value.transactionState.data
            if (state is Cash.State) {
                EasyBind.map(reportingExchange) { exchange ->
                    exchange.second(state.amount.withoutIssuer())
                }
            } else {
                ReadOnlyObjectWrapper(null)
            }
        }
    }

    init {
        Bindings.bindContent(topSplitPane.items, topSplitPaneNodesShown)

        // Transaction table
        Bindings.bindContent(transactionViewTable.items, viewerNodes)

        transactionViewTable.setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, column ->
            Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / transactionViewTable.columns.size).toInt()
        }

        transactionViewFiberId.setCellValueFactory { EasyBind.map (it.value.transactionId) { "${it.first ?: ""}" } }
        transactionViewClientUuid.setCellValueFactory { EasyBind.map (it.value.transactionId) { "${it.second ?: ""}" } }
        transactionViewProtocolStatus.setCellValueFactory { EasyBind.map(it.value.protocolStatus) { "${it ?: ""}" } }
        transactionViewTransactionStatus.setCellValueFactory { it.value.transactionStatus }
        transactionViewTransactionStatus.setCellFactory {
            object : TableCell<ViewerNode, TransactionCreateStatus?>() {
                val label = Label()
                override fun updateItem(
                        value: TransactionCreateStatus?,
                        empty: Boolean
                ) {
                    super.updateItem(value, empty)
                    if (value == null || empty) {
                        graphic = null
                        text = null
                    } else {
                        graphic = label
                        val backgroundFill = when (value) {
                            is TransactionCreateStatus.Started -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
                            is TransactionCreateStatus.Failed -> BackgroundFill(Color.SALMON, CornerRadii.EMPTY, Insets.EMPTY)
                            null -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
                        }
                        label.background = Background(backgroundFill)
                        label.text = "$value"
                    }
                }
            }
        }
        transactionViewStateMachineStatus.setCellValueFactory { it.value.stateMachineStatus }
        transactionViewStateMachineStatus.setCellFactory {
            object : TableCell<ViewerNode, StateMachineStatus?>() {
                val label = Label()
                override fun updateItem(
                        value: StateMachineStatus?,
                        empty: Boolean
                ) {
                    super.updateItem(value, empty)
                    if (value == null || empty) {
                        graphic = null
                        text = null
                    } else {
                        graphic = label
                        val backgroundFill = when (value) {
                            is StateMachineStatus.Added -> BackgroundFill(Color.LIGHTYELLOW, CornerRadii.EMPTY, Insets.EMPTY)
                            is StateMachineStatus.Removed -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
                            null -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
                        }
                        label.background = Background(backgroundFill)
                        label.text = "$value"
                    }
                }
            }
        }

        transactionViewCommandTypes.setCellValueFactory {
            EasyBind.map(it.value.commandTypes) { it.map { it.simpleName }.joinToString(",") }
        }
        transactionViewTotalValueEquiv.setCellValueFactory<ViewerNode, AmountDiff<Currency>> { it.value.totalValueEquiv }
        transactionViewTotalValueEquiv.cellFactory = object : Formatter<AmountDiff<Currency>> {
            override fun format(value: AmountDiff<Currency>) =
                    "${value.positivity.sign}${AmountFormatter.comma.format(value.amount)}"
        }.toTableCellFactory()

        // Contract states
        wireUpStatesTable(
                inputStateNodes,
                contractStatesInputsCountLabel,
                contractStatesInputStatesTable,
                contractStatesInputStatesId,
                contractStatesInputStatesType,
                contractStatesInputStatesOwner,
                contractStatesInputStatesLocalCurrency,
                contractStatesInputStatesAmount,
                contractStatesInputStatesEquiv
        )
        wireUpStatesTable(
                outputStateNodes,
                contractStatesOutputsCountLabel,
                contractStatesOutputStatesTable,
                contractStatesOutputStatesId,
                contractStatesOutputStatesType,
                contractStatesOutputStatesOwner,
                contractStatesOutputStatesLocalCurrency,
                contractStatesOutputStatesAmount,
                contractStatesOutputStatesEquiv
        )

        // Signatures
        Bindings.bindContent(signaturesList.items, signatures)
        signaturesList.cellFactory = object : Formatter<PublicKey> {
            override fun format(value: PublicKey) = value.toStringShort()
        }.toListCellFactory()

        // Low level events
        Bindings.bindContent(lowLevelEventsTable.items, lowLevelEvents)
        lowLevelEventsTimestamp.setCellValueFactory { ReadOnlyObjectWrapper(it.value.time) }
        lowLevelEventsEvent.setCellValueFactory { ReadOnlyObjectWrapper(it.value) }
        lowLevelEventsTable.setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, column ->
            Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / lowLevelEventsTable.columns.size).toInt()
        }

        matchingTransactionsLabel.textProperty().bind(EasyBind.map(Bindings.size(viewerNodes)) {
            "$it matching transaction${if (it == 1) "" else "s"}"
        })
    }
}
