package com.r3corda.explorer.views

import com.r3corda.client.fxutils.*
import com.r3corda.client.model.*
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
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
    private val transactionViewTransactionId: TableColumn<ViewerNode, String> by fxid()
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

    // Inject data
    private val gatheredTransactionDataList: ObservableList<out GatheredTransactionData>
            by observableListReadOnly(GatheredTransactionDataModel::gatheredTransactionDataList)
    private val reportingExchange: ObservableValue<Pair<Currency, (Amount<Currency>) -> Amount<Currency>>>
            by observableValue(ReportingCurrencyModel::reportingExchange)
    private val myIdentity: ObservableValue<Party> by observableValue(IdentityModel::myIdentity)

    /**
     * This is what holds data for a single transaction node. Note how a lot of these are nullable as we often simply don't
     * have the data.
     */
    data class ViewerNode(
            val transactionId: ObservableValue<SecureHash?>,
            val fiberId: ObservableValue<Long?>,
            val clientUuid: ObservableValue<UUID?>,
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

    /**
     * Holds information about a single input/output state, to be displayed in the [contractStatesTitledPane]
     */
    data class StateNode(
            val transactionState: TransactionState<*>,
            val stateRef: StateRef
    )

    /**
     * We map the gathered data about transactions almost one-to-one to the nodes.
     */
    private val viewerNodes = gatheredTransactionDataList.map {
        ViewerNode(
                transactionId = it.transaction.map { it?.id },
                fiberId = it.fiberId,
                clientUuid = it.uuid,
                /**
                 * We can't really do any better based on uuid, we need to store explicit data for this TODO
                 */
                originator = it.uuid.map { uuid ->
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
                commandTypes = it.transaction.map {
                    val commands = mutableSetOf<Class<CommandData>>()
                    it?.commands?.forEach {
                        commands.add(it.value.javaClass)
                    }
                    commands
                },
                totalValueEquiv = ::calculateTotalEquiv.lift(myIdentity, reportingExchange, it.transaction),
                transaction = it.transaction,
                allEvents = it.allEvents
        )
    }

    /**
     * The detail panes are only filled out if a transaction is selected
     */
    private val selectedViewerNode = transactionViewTable.singleRowSelection()
    private val selectedTransaction = selectedViewerNode.bind {
        when (it) {
            is SingleRowSelection.None -> null.lift()
            is SingleRowSelection.Selected -> it.node.transaction
        }
    }

    private val inputStateNodes = ChosenList(selectedTransaction.map {
        if (it == null) {
            FXCollections.emptyObservableList<StateNode>()
        } else {
            FXCollections.observableArrayList(it.inputs.map { StateNode(it.state, it.ref) })
        }
    })

    private val outputStateNodes = ChosenList(selectedTransaction.map {
        if (it == null) {
            FXCollections.emptyObservableList<StateNode>()
        } else {
            FXCollections.observableArrayList(it.outputs.mapIndexed { index, transactionState ->
                StateNode(transactionState, StateRef(it.id, index))
            })
        }
    })

    private val signatures = ChosenList(selectedTransaction.map {
        if (it == null) {
            FXCollections.emptyObservableList<PublicKey>()
        } else {
            FXCollections.observableArrayList(it.mustSign)
        }
    })

    private val lowLevelEvents = ChosenList(selectedViewerNode.map {
        when (it) {
            is SingleRowSelection.None -> FXCollections.emptyObservableList<ServiceToClientEvent>()
            is SingleRowSelection.Selected -> it.node.allEvents
        }
    })

    /**
     * We only display the detail panes if there is a node selected.
     */
    private val allNodesShown = FXCollections.observableArrayList<Node>(
            transactionViewTable,
            contractStatesTitledPane,
            signaturesTitledPane,
            lowLevelEventsTitledPane
    )
    private val onlyTransactionsTableShown = FXCollections.observableArrayList<Node>(
            transactionViewTable
    )
    private val topSplitPaneNodesShown = ChosenList(
            selectedViewerNode.map { selection ->
                if (selection is SingleRowSelection.None<*>) {
                    onlyTransactionsTableShown
                } else {
                    allNodesShown
                }
            })

    /**
     * Both input and output state tables look the same, so we each up with [wireUpStatesTable]
     */
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
        statesCountLabel.textProperty().bind(Bindings.size(states).map { "$it" })

        Bindings.bindContent(statesTable.items, states)

        statesId.setCellValueFactory { it.value.stateRef.toString().lift() }
        statesType.setCellValueFactory { it.value.transactionState.data.javaClass.lift() }
        statesOwner.setCellValueFactory {
            val state = it.value.transactionState.data
            if (state is OwnableState) {
                state.owner.toStringShort().lift()
            } else {
                "???".lift()
            }
        }
        statesLocalCurrency.setCellValueFactory {
            val state = it.value.transactionState.data
            if (state is Cash.State) {
                state.amount.token.product.lift()
            } else {
                null.lift()
            }
        }
        statesAmount.setCellValueFactory {
            val state = it.value.transactionState.data
            if (state is Cash.State) {
                state.amount.quantity.lift()
            } else {
                null.lift()
            }
        }
        statesAmount.setCellFactory(NumberFormatter.longComma.toTableCellFactory())
        statesEquiv.setCellValueFactory {
            val state = it.value.transactionState.data
            if (state is Cash.State) {
                reportingExchange.map { exchange ->
                    exchange.second(state.amount.withoutIssuer())
                }
            } else {
                null.lift()
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

        transactionViewTransactionId.setCellValueFactory { it.value.transactionId.map { "${it ?: ""}" } }
        transactionViewFiberId.setCellValueFactory { it.value.fiberId.map { "${it?: ""}" } }
        transactionViewClientUuid.setCellValueFactory { it.value.clientUuid.map { "${it ?: ""}" } }
        transactionViewProtocolStatus.setCellValueFactory { it.value.protocolStatus.map { "${it ?: ""}" } }
        transactionViewTransactionStatus.setCellValueFactory { it.value.transactionStatus }
        transactionViewTransactionStatus.setCustomCellFactory {
            val label = Label()
            val backgroundFill = when (it) {
                is TransactionCreateStatus.Started -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
                is TransactionCreateStatus.Failed -> BackgroundFill(Color.SALMON, CornerRadii.EMPTY, Insets.EMPTY)
                null -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
            }
            label.background = Background(backgroundFill)
            label.text = "$it"
            label
        }
        transactionViewStateMachineStatus.setCellValueFactory { it.value.stateMachineStatus }
        transactionViewStateMachineStatus.setCustomCellFactory {
            val label = Label()
            val backgroundFill = when (it) {
                is StateMachineStatus.Added -> BackgroundFill(Color.LIGHTYELLOW, CornerRadii.EMPTY, Insets.EMPTY)
                is StateMachineStatus.Removed -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
                null -> BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)
            }
            label.background = Background(backgroundFill)
            label.text = "$it"
            label
        }

        transactionViewCommandTypes.setCellValueFactory {
            it.value.commandTypes.map { it.map { it.simpleName }.joinToString(",") }
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
        lowLevelEventsTimestamp.setCellValueFactory { it.value.time.lift() }
        lowLevelEventsEvent.setCellValueFactory { it.value.lift() }
        lowLevelEventsTable.setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, column ->
            Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / lowLevelEventsTable.columns.size).toInt()
        }

        matchingTransactionsLabel.textProperty().bind(EasyBind.map(Bindings.size(viewerNodes)) {
            "$it matching transaction${if (it == 1) "" else "s"}"
        })
    }
}

/**
 * We calculate the total value by subtracting relevant input states and adding relevant output states, as long as they're cash
 */
private fun calculateTotalEquiv(
        identity: Party,
        reportingCurrencyExchange: Pair<Currency, (Amount<Currency>) -> Amount<Currency>>,
        transaction: LedgerTransaction?): AmountDiff<Currency>? {
    if (transaction == null) {
        return null
    }
    var sum = 0L
    val (reportingCurrency, exchange) = reportingCurrencyExchange
    val publicKey = identity.owningKey
    transaction.inputs.forEach {
        val contractState = it.state.data
        if (contractState is Cash.State && publicKey == contractState.owner) {
            sum -= exchange(contractState.amount.withoutIssuer()).quantity
        }
    }
    transaction.outputs.forEach {
        val contractState = it.data
        if (contractState is Cash.State && publicKey == contractState.owner) {
            sum += exchange(contractState.amount.withoutIssuer()).quantity
        }
    }
    return AmountDiff.fromLong(sum, reportingCurrency)
}

