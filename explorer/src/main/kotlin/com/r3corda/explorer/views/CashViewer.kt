package com.r3corda.explorer.views

import com.r3corda.client.fxutils.AggregatedList
import com.r3corda.client.fxutils.ChosenList
import com.r3corda.client.model.ContractStateModel
import com.r3corda.client.model.observableList
import com.r3corda.client.model.observableValue
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.withoutIssuer
import com.r3corda.core.crypto.Party
import com.r3corda.explorer.formatters.AmountFormatter
import com.r3corda.explorer.formatters.NumberFormatter
import com.r3corda.explorer.model.ReportingCurrencyModel
import com.r3corda.explorer.model.SettingsModel
import com.r3corda.explorer.ui.SingleRowSelection
import com.r3corda.explorer.ui.setColumnPrefWidthPolicy
import com.r3corda.explorer.ui.singleRowSelection
import com.r3corda.explorer.ui.toTreeTableCellFactory
import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.ReadOnlyStringWrapper
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlinx.support.jdk8.collections.stream
import org.fxmisc.easybind.EasyBind
import tornadofx.UIComponent
import tornadofx.View
import tornadofx.selectedItem
import java.time.LocalDateTime
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors

sealed class FilterCriteria {
    abstract fun matches(string: String): Boolean

    object All : FilterCriteria() {
        override fun matches(string: String) = true
    }

    class FilterString(val filterString: String) : FilterCriteria() {
        override fun matches(string: String) = string.contains(filterString)
    }
}

class CashViewer : View() {
    // Inject UI elements
    override val root: SplitPane by fxml()

    val topSplitPane: SplitPane by fxid()
    // Left pane
    val leftPane: VBox by fxid()
    val searchCriteriaTextField: TextField by fxid()
    val searchCancelImageView: ImageView by fxid()
    val totalMatchingLabel: Label by fxid()
    val cashViewerTable: TreeTableView<ViewerNode> by fxid()
    val cashViewerTableIssuerCurrency: TreeTableColumn<ViewerNode, String> by fxid()
    val cashViewerTableLocalCurrency: TreeTableColumn<ViewerNode, Amount<Currency>?> by fxid()
    val cashViewerTableEquiv: TreeTableColumn<ViewerNode, Amount<Currency>?> by fxid()

    // Right pane
    val rightPane: VBox by fxid()
    val totalPositionsLabel: Label by fxid()
    val equivSumLabel: Label by fxid()
    val cashStatesList: ListView<StateRow> by fxid()

    // Inject observables
    val cashStates by observableList(ContractStateModel::cashStates)
    val reportingCurrency: ObservableValue<Currency> by observableValue(SettingsModel::reportingCurrency)
    val reportingExchange: ObservableValue<Pair<Currency, (Amount<Currency>) -> Amount<Currency>>>
            by observableValue(ReportingCurrencyModel::reportingExchange)

    sealed class ViewerNode {
        object Root : ViewerNode()
        class IssuerNode(
                val issuer: Party,
                val sumEquivAmount: ObservableValue<Amount<Currency>>,
                val states: ObservableList<StateAndRef<Cash.State>>
        ) : ViewerNode()
        class CurrencyNode(
                val amount: ObservableValue<Amount<Currency>>,
                val equivAmount: ObservableValue<Amount<Currency>>,
                val states: ObservableList<StateAndRef<Cash.State>>
        ) : ViewerNode()
    }

    private val filterCriteria = EasyBind.map(searchCriteriaTextField.textProperty()) { text ->
        if (text == "") {
            FilterCriteria.All
        } else {
            FilterCriteria.FilterString(text)
        }
    }

    private val issueFilteredCashStates = FilteredList(cashStates).apply {
        predicateProperty().bind(EasyBind.map(filterCriteria) { filterCriteria ->
            Predicate<StateAndRef<Cash.State>> {
                filterCriteria.matches(it.state.data.amount.token.issuer.party.toString())
            }
        })
    }

    private val currencyFilteredCashStates = FilteredList(cashStates).apply {
        predicateProperty().bind(EasyBind.map(filterCriteria) { filterCriteria ->
            Predicate<StateAndRef<Cash.State>> {
                filterCriteria.matches(it.state.data.amount.token.product.toString())
            }
        })
    }


    enum class FilterMethod {
        Issue,
        Currency
    }

    private val filterMethod = Bindings.createObjectBinding({
        if (issueFilteredCashStates.size > currencyFilteredCashStates.size) {
            FilterMethod.Issue
        } else {
            FilterMethod.Currency
        }
    }, arrayOf(filterCriteria))

    private val filteredCashStates = ChosenList<StateAndRef<Cash.State>>(EasyBind.map(filterMethod) {
        when (it) {
            FilterMethod.Issue -> issueFilteredCashStates
            FilterMethod.Currency -> currencyFilteredCashStates
            null -> issueFilteredCashStates
        }
    })

    val cashViewerIssueNodes: ObservableList<TreeItem<ViewerNode.IssuerNode>> =
            AggregatedList(filteredCashStates, { it.state.data.amount.token.issuer.party }) { issuer, memberStates ->
                val currencyNodes = AggregatedList(memberStates, { it.state.data.amount.token.product }) { currency, memberStates ->
                    val sumAmount = EasyBind.map(
                            Bindings.createLongBinding({
                                memberStates.stream().collect(Collectors.summingLong { it.state.data.amount.quantity })
                            }, arrayOf(memberStates))
                    ) { sum -> Amount(sum.toLong(), currency) }

                    val equivSumAmount = EasyBind.combine(sumAmount, reportingExchange) { sum, exchange ->
                        exchange.second(sum)
                    }
                    TreeItem(ViewerNode.CurrencyNode(sumAmount, equivSumAmount, memberStates))
                }

                val equivSumAmount =
                        EasyBind.combine(
                                EasyBind.combine(EasyBind.map(currencyNodes, { it.value.equivAmount })) {
                                    it.collect(Collectors.summingLong(Amount<Currency>::quantity))
                                },
                                reportingCurrency
                        ) { sum, currency ->
                            Amount(sum.toLong(), currency)
                        }

                val treeItem = TreeItem(ViewerNode.IssuerNode(issuer, equivSumAmount, memberStates))
                treeItem.isExpanded = true
                @Suppress("UNCHECKED_CAST")
                Bindings.bindContent(treeItem.children as ObservableList<TreeItem<out ViewerNode>>, currencyNodes)
                treeItem
            }

    val selectedViewerNode = cashViewerTable.singleRowSelection()

    data class StateRow (
        val originated: LocalDateTime,
        val stateAndRef: StateAndRef<Cash.State>
    )

    inner class StateRowGraphic(
            val stateRow: StateRow
    ) : UIComponent() {
        override val root: HBox by fxml("CashStateViewer.fxml")

        val equivLabel: Label by fxid()
        val stateIdValueLabel: Label by fxid()
        val issuerValueLabel: Label by fxid()
        val originatedValueLabel: Label by fxid()
        val amountValueLabel: Label by fxid()
        val equivValueLabel: Label by fxid()

        val equivAmount: ObservableValue<Amount<Currency>> = EasyBind.map(reportingExchange) {
            it.second(stateRow.stateAndRef.state.data.amount.withoutIssuer())
        }

        init {
            val amountNoIssuer = stateRow.stateAndRef.state.data.amount.withoutIssuer()
            val amountFormatter = AmountFormatter.currency(AmountFormatter.comma)
            val equivFormatter = AmountFormatter.comma

            equivLabel.textProperty().bind(EasyBind.map(equivAmount) { it.token.currencyCode.toString() })
            stateIdValueLabel.text = stateRow.stateAndRef.ref.toString()
            issuerValueLabel.text = stateRow.stateAndRef.state.data.amount.token.issuer.toString()
            originatedValueLabel.text = stateRow.originated.toString()
            amountValueLabel.text = amountFormatter.format(amountNoIssuer)
            equivValueLabel.textProperty().bind(EasyBind.map(equivAmount) { equivFormatter.format(it) })
        }
    }

    private val noSelectionStates = FXCollections.observableArrayList<StateAndRef<Cash.State>>()
    private val selectedViewerNodeStates = ChosenList<StateAndRef<Cash.State>>(EasyBind.map(selectedViewerNode) { selection ->
        when (selection) {
            is SingleRowSelection.None -> noSelectionStates
            is SingleRowSelection.Selected ->
                when (selection.node) {
                    CashViewer.ViewerNode.Root -> noSelectionStates
                    is CashViewer.ViewerNode.IssuerNode -> selection.node.states
                    is CashViewer.ViewerNode.CurrencyNode -> selection.node.states
                }
        }
    })

    private val noSelectionSumEquiv = EasyBind.map(reportingCurrency) { Amount(0, it) }
    private val selectedViewerNodeSumEquiv = EasyBind.monadic(selectedViewerNode).flatMap { selection ->
        when (selection) {
            is SingleRowSelection.None -> noSelectionSumEquiv
            is SingleRowSelection.Selected ->
                when (selection.node) {
                    ViewerNode.Root -> noSelectionSumEquiv
                    is ViewerNode.IssuerNode -> selection.node.sumEquivAmount
                    is ViewerNode.CurrencyNode -> selection.node.equivAmount
                }
        }
    }

    private val stateRows = EasyBind.map(selectedViewerNodeStates) {
        StateRow(LocalDateTime.now(), it)
    }

    private val onlyLeftPaneShown = FXCollections.observableArrayList<Node>(leftPane)
    private val bothPanesShown = FXCollections.observableArrayList<Node>(leftPane, rightPane)
    private val panesShown = ChosenList<Node>(EasyBind.map(selectedViewerNode) {
        when (it) {
            is SingleRowSelection.None -> onlyLeftPaneShown
            is SingleRowSelection.Selected -> bothPanesShown
        }
    })

    // Wire up UI
    init {

        searchCancelImageView.setOnMouseClicked { event: MouseEvent ->
            if (event.button == MouseButton.PRIMARY) {
                searchCriteriaTextField.text = ""
            }
        }

        Bindings.bindContent(topSplitPane.items, panesShown)

        rightPane.visibleProperty().bind(EasyBind.map(selectedViewerNode) {
            it !is SingleRowSelection.None
        })

        totalPositionsLabel.textProperty().bind(Bindings.createStringBinding({
            val positionsCount = selectedViewerNodeStates.size
            val plural = if (positionsCount == 1) "" else "s"
            "Total $positionsCount position$plural"
        }, arrayOf(selectedViewerNodeStates)))

        val equivSumLabelFormatter = AmountFormatter.currency(AmountFormatter.kmb(NumberFormatter.doubleComma))
        equivSumLabel.textProperty().bind(EasyBind.map(selectedViewerNodeSumEquiv) {
            equivSumLabelFormatter.format(it)
        })

        Bindings.bindContent(cashStatesList.items, stateRows)

        cashStatesList.setCellFactory {
            object : ListCell<StateRow>() {
                init {
                    text = null
                }
                override fun updateItem(value: StateRow?, empty: Boolean) {
                    super.updateItem(value, empty)
                    graphic = if (value != null && !empty) {
                        StateRowGraphic(value).root
                    } else {
                        null
                    }
                }
            }
        }

        val cellFactory = AmountFormatter.comma.toTreeTableCellFactory<ViewerNode, Amount<Currency>>()

        cashViewerTable.setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, column ->
            Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / cashViewerTable.columns.size).toInt()
        }

        cashViewerTableIssuerCurrency.setCellValueFactory {
            val node = it.value.value
            when (node) {
                ViewerNode.Root -> ReadOnlyStringWrapper("")
                is ViewerNode.IssuerNode -> ReadOnlyStringWrapper(node.issuer.toString())
                is ViewerNode.CurrencyNode -> EasyBind.map(node.amount) { it.token.toString() }
            }
        }
        cashViewerTableLocalCurrency.setCellValueFactory {
            val node = it.value.value
            when (node) {
                ViewerNode.Root -> ReadOnlyObjectWrapper(null)
                is ViewerNode.IssuerNode -> ReadOnlyObjectWrapper(null)
                is ViewerNode.CurrencyNode -> EasyBind.map(node.amount) { it }
            }
        }
        cashViewerTableLocalCurrency.cellFactory = cellFactory
        cashViewerTableLocalCurrency.isSortable = false
        cashViewerTableEquiv.setCellValueFactory {
            val node = it.value.value
            when (node) {
                ViewerNode.Root -> ReadOnlyObjectWrapper(null)
                is ViewerNode.IssuerNode -> EasyBind.map(node.sumEquivAmount) { it }
                is ViewerNode.CurrencyNode -> EasyBind.map(node.equivAmount) { it }
            }
        }
        cashViewerTableEquiv.cellFactory = cellFactory
        cashViewerTableEquiv.textProperty().bind(EasyBind.map(reportingCurrency) { "$it Equiv" })

        cashViewerTable.root = TreeItem(ViewerNode.Root)
        @Suppress("UNCHECKED_CAST")
        Bindings.bindContent(cashViewerTable.root.children as ObservableList<TreeItem<out ViewerNode>>, cashViewerIssueNodes)

        cashViewerTable.root.isExpanded = true
        cashViewerTable.isShowRoot = false

        totalMatchingLabel.textProperty().bind(EasyBind.map(
                Bindings.createIntegerBinding({ cashViewerIssueNodes.size }, arrayOf(cashViewerIssueNodes))
        ) {
            val plural = if (it == 1) "" else "s"
            "Total $it matching issuer$plural"
        })
    }
}
