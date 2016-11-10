package net.corda.explorer.views

import net.corda.client.fxutils.*
import net.corda.client.model.*
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.node.NodeInfo
import net.corda.core.protocols.StateMachineRunId
import net.corda.explorer.AmountDiff
import net.corda.explorer.formatters.AmountFormatter
import net.corda.explorer.identicon.identicon
import net.corda.explorer.identicon.identiconToolTip
import net.corda.explorer.model.ReportingCurrencyModel
import net.corda.explorer.sign
import net.corda.explorer.ui.setCustomCellFactory
import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TableView
import javafx.scene.control.TitledPane
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.BorderPane
import javafx.scene.layout.CornerRadii
import javafx.scene.paint.Color
import tornadofx.*
import java.security.PublicKey
import java.util.*

class TransactionViewer : View() {
    override val root by fxml<BorderPane>()
    private val transactionViewTable by fxid<TableView<ViewerNode>>()
    private val matchingTransactionsLabel by fxid<Label>()
    // Inject data
    private val gatheredTransactionDataList  by observableListReadOnly(GatheredTransactionDataModel::gatheredTransactionDataList)
    private val reportingExchange by observableValue(ReportingCurrencyModel::reportingExchange)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)

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
            val totalValueEquiv: ObservableValue<AmountDiff<Currency>>
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
                    val resolvedInputs = it.transaction.inputs.sequence()
                            .map { (it as? PartiallyResolvedTransaction.InputResolution.Resolved)?.stateAndRef?.state }
                            .filterNotNull().toList().lift()

                    ::calculateTotalEquiv.lift(
                            myIdentity,
                            reportingExchange,
                            resolvedInputs,
                            it.transaction.transaction.tx.outputs.lift()
                    )
                }()
        )
    }

    init {
        val searchField = SearchField(viewerNodes, arrayOf({ viewerNode, s -> viewerNode.commandTypes.any { it.simpleName.contains(s, true) } }))
        root.top = searchField.root
        // Transaction table
        transactionViewTable.apply {
            items = searchField.filteredData
            column("Transaction ID", ViewerNode::transactionId).setCustomCellFactory {
                label("$it".substring(0, 16) + "...") {
                    graphic = imageview {
                        image = identicon(it, 5.0)
                    }
                    tooltip = identiconToolTip(it)
                }
            }
            column("State Machine ID", ViewerNode::stateMachineRunId).cellFormat { text = "${it?.uuid ?: ""}" }
            column("Protocol status", ViewerNode::protocolStatus).cellFormat { text = "${it.value ?: ""}" }
            column("SM Status", ViewerNode::stateMachineStatus).cellFormat { text = "${it.value ?: ""}" }
            column("Command type(s)", ViewerNode::commandTypes).cellFormat { text = it.map { it.simpleName }.joinToString(",") }
            column("Total value (USD equiv)", ViewerNode::totalValueEquiv)
                    .cellFormat { text = "${it.positivity.sign}${AmountFormatter.boring.format(it.amount)}" }
            rowExpander(true) {
                add(ContractStatesView(it.transaction).root)
                background = Background(BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY))
                prefHeight = 400.0
            }.apply {
                // Hide the expander column.
                isVisible = false
                prefWidth = 0.0
            }
        }

        matchingTransactionsLabel.textProperty().bind(Bindings.size(transactionViewTable.items).map {
            "$it matching transaction${if (it == 1) "" else "s"}"
        })
    }

    private class ContractStatesView(val transaction: PartiallyResolvedTransaction) : View() {
        override val root: Parent by fxml()
        private val inputs: ListView<StateNode> by fxid()
        private val outputs: ListView<StateNode> by fxid()
        private val signatures: ListView<PublicKey> by fxid()
        private val inputPane: TitledPane by fxid()
        private val outputPane: TitledPane by fxid()
        private val signaturesPane: TitledPane by fxid()

        init {
            val inputStates = transaction.inputs.map { StateNode(it, it.value.stateRef) }
            val outputStates = transaction.transaction.tx.outputs.mapIndexed { index, transactionState ->
                val stateRef = StateRef(transaction.id, index)
                StateNode(PartiallyResolvedTransaction.InputResolution.Resolved(StateAndRef(transactionState, stateRef)).lift(), stateRef)
            }

            val signatureData = transaction.transaction.sigs.map { it.by }
            // Bind count to TitlePane
            inputPane.textProperty().bind(inputStates.lift().map { "Input (${it.count()})" })
            outputPane.textProperty().bind(outputStates.lift().map { "Output (${it.count()})" })
            signaturesPane.textProperty().bind(signatureData.lift().map { "Signatures (${it.count()})" })

            val cellFactory = { node: StateNode ->
                (node.state.value as? PartiallyResolvedTransaction.InputResolution.Resolved)?.run {
                    val data = stateAndRef.state.data
                    form {
                        label("${data.contract.javaClass.simpleName} (${stateAndRef.ref.toString().substring(0, 16)}...)[${stateAndRef.ref.index}]") {
                            graphic = imageview {
                                image = identicon(stateAndRef.ref.txhash, 10.0)
                            }
                            tooltip = identiconToolTip(stateAndRef.ref.txhash)
                        }
                        when (data) {
                            is Cash.State -> form {
                                fieldset {
                                    field("Amount :") {
                                        label(AmountFormatter.boring.format(data.amount.withoutIssuer()))
                                    }
                                    field("Issuer :") {
                                        label("${data.amount.token.issuer}") {
                                            tooltip(data.amount.token.issuer.party.owningKey.toStringShort())
                                        }
                                    }
                                    field("Owner :") {
                                        val owner = data.owner
                                        val nodeInfo = Models.get<NetworkIdentityModel>(TransactionViewer::class).lookup(owner)
                                        label(nodeInfo?.legalIdentity?.name ?: "???") {
                                            tooltip(data.owner.toStringShort())
                                        }
                                    }
                                }
                            }
                        // TODO : Generic view using reflection?
                            else -> label {}
                        }
                    }
                } ?: label { text = "???" }
            }

            inputs.setCustomCellFactory(cellFactory)
            outputs.setCustomCellFactory(cellFactory)

            inputs.items = FXCollections.observableList(inputStates)
            outputs.items = FXCollections.observableList(outputStates)
            signatures.items = FXCollections.observableList(signatureData)

            signatures.apply {
                cellFormat { key ->
                    val nodeInfo = Models.get<NetworkIdentityModel>(TransactionViewer::class).lookup(key)
                    text = "${key.toStringShort()} (${nodeInfo?.legalIdentity?.name ?: "???"})"
                }
                prefHeight = 185.0
            }
        }
    }
}

/**
 * We calculate the total value by subtracting relevant input states and adding relevant output states, as long as they're cash
 */
private fun calculateTotalEquiv(identity: NodeInfo?,
                                reportingCurrencyExchange: Pair<Currency, (Amount<Currency>) -> Amount<Currency>>,
                                inputs: List<TransactionState<ContractState>>,
                                outputs: List<TransactionState<ContractState>>): AmountDiff<Currency> {
    val (reportingCurrency, exchange) = reportingCurrencyExchange
    val publicKey = identity?.legalIdentity?.owningKey
    fun List<TransactionState<ContractState>>.sum(): Long {
        return this.map { it.data as? Cash.State }
                .filterNotNull()
                .filter { publicKey == it.owner }
                .map { exchange(it.amount.withoutIssuer()).quantity }
                .sum()
    }
    return AmountDiff.fromLong(outputs.sum() - inputs.sum(), reportingCurrency)
}
