package com.r3corda.explorer.views

import com.r3corda.client.fxutils.*
import com.r3corda.client.model.*
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.protocols.StateMachineRunId
import com.r3corda.explorer.AmountDiff
import com.r3corda.explorer.formatters.AmountFormatter
import com.r3corda.explorer.formatters.Formatter
import com.r3corda.explorer.formatters.NumberFormatter
import com.r3corda.explorer.model.IdentityModel
import com.r3corda.explorer.model.ReportingCurrencyModel
import com.r3corda.explorer.sign
import com.r3corda.explorer.ui.*
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
import tornadofx.View
import java.security.PublicKey
import java.util.*

class TransactionViewer: View() {
    override val root: VBox by fxml()

    val topSplitPane: SplitPane by fxid()

    // Top half (transactions table)
    private val transactionViewTable: TableView<ViewerNode> by fxid()
    private val transactionViewTransactionId: TableColumn<ViewerNode, String> by fxid()
    private val transactionViewStateMachineId: TableColumn<ViewerNode, String> by fxid()
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
    private val contractStatesInputStatesType: TableColumn<StateNode, String> by fxid()
    private val contractStatesInputStatesOwner: TableColumn<StateNode, String> by fxid()
    private val contractStatesInputStatesLocalCurrency: TableColumn<StateNode, Currency?> by fxid()
    private val contractStatesInputStatesAmount: TableColumn<StateNode, Long?> by fxid()
    private val contractStatesInputStatesEquiv: TableColumn<StateNode, Amount<Currency>?> by fxid()

    private val contractStatesOutputsCountLabel: Label by fxid()
    private val contractStatesOutputStatesTable: TableView<StateNode> by fxid()
    private val contractStatesOutputStatesId: TableColumn<StateNode, String> by fxid()
    private val contractStatesOutputStatesType: TableColumn<StateNode, String> by fxid()
    private val contractStatesOutputStatesOwner: TableColumn<StateNode, String> by fxid()
    private val contractStatesOutputStatesLocalCurrency: TableColumn<StateNode, Currency?> by fxid()
    private val contractStatesOutputStatesAmount: TableColumn<StateNode, Long?> by fxid()
    private val contractStatesOutputStatesEquiv: TableColumn<StateNode, Amount<Currency>?> by fxid()

    private val signaturesTitledPane: TitledPane by fxid()
    private val signaturesList: ListView<PublicKey> by fxid()

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
            val transaction: PartiallyResolvedTransaction,
            val transactionId: SecureHash,
            val stateMachineRunId: ObservableValue<StateMachineRunId?>,
            val stateMachineStatus: ObservableValue<out StateMachineStatus?>,
            val protocolStatus: ObservableValue<out ProtocolStatus?>,
            val commandTypes: Collection<Class<CommandData>>,
            val totalValueEquiv: ObservableValue<AmountDiff<Currency>?>
    )

    /**
     * Holds information about a single input/output state, to be displayed in the [contractStatesTitledPane]
     */
    data class StateNode(
            val state: ObservableValue<PartiallyResolvedTransaction.InputResolution>,
            val stateRef: StateRef
    )

    /**
     * We map the gathered data about transactions almost one-to-one to the nodes.
     */
    private val viewerNodes = gatheredTransactionDataList.map {
        // TODO in theory there may be several associated state machines, we should at least give a warning if there are
        // several, currently we just throw others away
        val stateMachine = it.stateMachines.first()
        fun <A> stateMachineProperty(property: (StateMachineData) -> ObservableValue<out A?>): ObservableValue<out A?> {
            return stateMachine.map { it?.let(property) }.bindOut { it ?: null.lift() }
        }
        ViewerNode(
                transaction = it.transaction,
                transactionId = it.transaction.id,
                stateMachineRunId = stateMachine.map { it?.id },
                protocolStatus = stateMachineProperty { it.protocolStatus },
                stateMachineStatus = stateMachineProperty { it.stateMachineStatus },
                commandTypes = it.transaction.transaction.tx.commands.map { it.value.javaClass },
                totalValueEquiv = {
                    val resolvedInputs = it.transaction.inputs.sequence().map { resolution ->
                        when (resolution) {
                            is PartiallyResolvedTransaction.InputResolution.Unresolved -> null
                            is PartiallyResolvedTransaction.InputResolution.Resolved -> resolution.stateAndRef
                        }
                    }.fold(listOf()) { inputs: List<StateAndRef<ContractState>>?, state: StateAndRef<ContractState>? ->
                        if (inputs != null && state != null) {
                            inputs + state
                        } else {
                            null
                        }
                    }

                    ::calculateTotalEquiv.lift(
                            myIdentity,
                            reportingExchange,
                            resolvedInputs.lift(),
                            it.transaction.transaction.tx.outputs.lift()
                    )
                }()
        )
    }

    /**
     * The detail panes are only filled out if a transaction is selected
     */
    private val selectedViewerNode = transactionViewTable.singleRowSelection()
    private val selectedTransaction = selectedViewerNode.map {
        when (it) {
            is SingleRowSelection.None -> null
            is SingleRowSelection.Selected -> it.node.transaction
        }
    }

    private val inputStateNodes = ChosenList(selectedTransaction.map { transaction ->
        if (transaction == null) {
            FXCollections.emptyObservableList<StateNode>()
        } else {
            FXCollections.observableArrayList(transaction.inputs.map { inputResolution ->
                StateNode(inputResolution, inputResolution.value.stateRef)
            })
        }
    })

    private val outputStateNodes = ChosenList(selectedTransaction.map {
        if (it == null) {
            FXCollections.emptyObservableList<StateNode>()
        } else {
            FXCollections.observableArrayList(it.transaction.tx.outputs.mapIndexed { index, transactionState ->
                val stateRef = StateRef(it.id, index)
                StateNode(PartiallyResolvedTransaction.InputResolution.Resolved(StateAndRef(transactionState, stateRef)).lift(), stateRef)
            })
        }
    })

    private val signatures = ChosenList(selectedTransaction.map {
        if (it == null) {
            FXCollections.emptyObservableList<PublicKey>()
        } else {
            FXCollections.observableArrayList(it.transaction.sigs.map { it.by })
        }
    })

    /**
     * We only display the detail panes if there is a node selected.
     */
    private val allNodesShown = FXCollections.observableArrayList<Node>(
            transactionViewTable,
            contractStatesTitledPane,
            signaturesTitledPane
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
            statesType: TableColumn<StateNode, String>,
            statesOwner: TableColumn<StateNode, String>,
            statesLocalCurrency: TableColumn<StateNode, Currency?>,
            statesAmount: TableColumn<StateNode, Long?>,
            statesEquiv: TableColumn<StateNode, Amount<Currency>?>
    ) {
        statesCountLabel.textProperty().bind(Bindings.size(states).map { "$it" })

        Bindings.bindContent(statesTable.items, states)

        val unknownString = "???"

        statesId.setCellValueFactory { it.value.stateRef.toString().lift() }
        statesType.setCellValueFactory {
            resolvedOrDefault(it.value.state, unknownString) {
                it.state.data.javaClass.toString()
            }
        }
        statesOwner.setCellValueFactory {
            resolvedOrDefault(it.value.state, unknownString) {
                val contractState = it.state.data
                if (contractState is OwnableState) {
                    contractState.owner.toStringShort()
                } else {
                    unknownString
                }
            }
        }
        statesLocalCurrency.setCellValueFactory {
            resolvedOrDefault<Currency?>(it.value.state, null) {
                val contractState = it.state.data
                if (contractState is Cash.State) {
                    contractState.amount.token.product
                } else {
                    null
                }
            }
        }
        statesAmount.setCellValueFactory {
            resolvedOrDefault<Long?>(it.value.state, null) {
                val contractState = it.state.data
                if (contractState is Cash.State) {
                    contractState.amount.quantity
                } else {
                    null
                }
            }
        }
        statesAmount.cellFactory = NumberFormatter.boringLong.toTableCellFactory()
        statesEquiv.setCellValueFactory {
            resolvedOrDefault<ObservableValue<Amount<Currency>?>>(it.value.state, null.lift()) {
                val contractState = it.state.data
                if (contractState is Cash.State) {
                    reportingExchange.map { exchange ->
                        exchange.second(contractState.amount.withoutIssuer())
                    }
                } else {
                    null.lift()
                }
            }.bind { it }

        }
        statesEquiv.cellFactory = AmountFormatter.boring.toTableCellFactory()
    }

    init {
        Bindings.bindContent(topSplitPane.items, topSplitPaneNodesShown)

        // Transaction table
        Bindings.bindContent(transactionViewTable.items, viewerNodes)

        transactionViewTable.setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, column ->
            Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / transactionViewTable.columns.size).toInt()
        }

        transactionViewTransactionId.setCellValueFactory { "${it.value.transactionId}".lift() }
        transactionViewStateMachineId.setCellValueFactory { it.value.stateMachineRunId.map { "${it?.uuid ?: ""}" } }
        transactionViewProtocolStatus.setCellValueFactory { it.value.protocolStatus.map { "${it ?: ""}" } }
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
        transactionViewStateMachineStatus.setCellValueFactory { it.value.stateMachineStatus.map { it } }
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
            it.value.commandTypes.map { it.simpleName }.joinToString(",").lift()
        }
        transactionViewTotalValueEquiv.setCellValueFactory<ViewerNode, AmountDiff<Currency>> { it.value.totalValueEquiv }
        transactionViewTotalValueEquiv.cellFactory = object : Formatter<AmountDiff<Currency>> {
            override fun format(value: AmountDiff<Currency>) =
                    "${value.positivity.sign}${AmountFormatter.boring.format(value.amount)}"
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

        matchingTransactionsLabel.textProperty().bind(Bindings.size(viewerNodes).map {
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
        inputs: List<StateAndRef<ContractState>>?,
        outputs: List<TransactionState<ContractState>>): AmountDiff<Currency>? {
    if (inputs == null) {
        return null
    }
    var sum = 0L
    val (reportingCurrency, exchange) = reportingCurrencyExchange
    val publicKey = identity.owningKey
    inputs.forEach {
        val contractState = it.state.data
        if (contractState is Cash.State && publicKey == contractState.owner) {
            sum -= exchange(contractState.amount.withoutIssuer()).quantity
        }
    }
    outputs.forEach {
        val contractState = it.data
        if (contractState is Cash.State && publicKey == contractState.owner) {
            sum += exchange(contractState.amount.withoutIssuer()).quantity
        }
    }
    return AmountDiff.fromLong(sum, reportingCurrency)
}

fun <A> resolvedOrDefault(
        state: ObservableValue<PartiallyResolvedTransaction.InputResolution>,
        default: A,
        resolved: (StateAndRef<*>) -> A
): ObservableValue<A> {
    return state.map {
        when (it) {
            is PartiallyResolvedTransaction.InputResolution.Unresolved -> default
            is PartiallyResolvedTransaction.InputResolution.Resolved -> resolved(it.stateAndRef)
        }
    }
}
