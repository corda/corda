package com.r3corda.explorer.views

import com.r3corda.client.fxutils.*
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
import com.r3corda.explorer.ui.*
import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.fxmisc.easybind.EasyBind
import tornadofx.UIComponent
import tornadofx.View
import java.time.LocalDateTime
import java.util.*

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

    /**
     * This holds the data for each row in the TreeTable.
     */
    sealed class ViewerNode {
        object Root : ViewerNode()
        class IssuerNode(
                val issuer: Party,
                val sumEquivAmount: ObservableValue<out Amount<Currency>>,
                val states: ObservableList<StateAndRef<Cash.State>>
        ) : ViewerNode()
        class CurrencyNode(
                val amount: ObservableValue<Amount<Currency>>,
                val equivAmount: ObservableValue<Amount<Currency>>,
                val states: ObservableList<StateAndRef<Cash.State>>
        ) : ViewerNode()
    }

    /**
     * We allow filtering by both issuer and currency. We do this by filtering by both at the same time and picking the
     * one which produces more results, which seems to work, as the set of currency strings don't really overlap with
     * issuer strings.
     */

    /**
     * Holds the filtering criterion based on the input text
     */
    private val filterCriteria = searchCriteriaTextField.textProperty().map { text ->
        if (text.isBlank()) {
            FilterCriteria.All
        } else {
            FilterCriteria.FilterString(text)
        }
    }

    /**
     * Filter cash states based on issuer.
     */
    private val issueFilteredCashStates = cashStates.filter(filterCriteria.map { criteria ->
        { state: StateAndRef<Cash.State> ->
            criteria.matches(state.state.data.amount.token.issuer.party.toString())
        }
    })
    /**
     * Now filter cash states based on currency.
     */
    private val currencyFilteredCashStates = cashStates.filter(filterCriteria.map { criteria ->
        { state: StateAndRef<Cash.State> ->
            criteria.matches(state.state.data.amount.token.product.toString())
        }
    })

    /**
     * Now we pick which one to use.
     */
    private val filteredCashStates = ChosenList(filterCriteria.map {
        if (issueFilteredCashStates.size > currencyFilteredCashStates.size) {
            issueFilteredCashStates
        } else {
            currencyFilteredCashStates
        }
    })

    /**
     * This is where we aggregate the list of cash states into the TreeTable structure.
     */
    val cashViewerIssueNodes: ObservableList<TreeItem<out ViewerNode.IssuerNode>> =
            /**
             * First we group the states based on the issuer. [memberStates] is all states holding currency issued by [issuer]
             */
            AggregatedList(filteredCashStates, { it.state.data.amount.token.issuer.party }) { issuer, memberStates ->
                /**
                 * Next we create subgroups based on currency. [memberStates] here is all states holding currency [currency] issued by [issuer] above.
                 * Note that these states will not be displayed in the TreeTable, but rather in the side pane if the user clicks on the row.
                 */
                val currencyNodes = AggregatedList(memberStates, { it.state.data.amount.token.product }) { currency, memberStates ->
                    /**
                     * We sum the states in the subgroup, to be displayed in the "Local Currency" column
                     */
                    val amounts = memberStates.map { it.state.data.amount.withoutIssuer() }
                    val sumAmount = amounts.fold(Amount(0, currency), Amount<Currency>::plus)

                    /**
                     * We exchange the sum to the reporting currency, to be displayed in the "<currency> Equiv" column.
                     */
                    val equivSumAmount = EasyBind.combine(sumAmount, reportingExchange) { sum, exchange ->
                        exchange.second(sum)
                    }
                    /**
                     * Finally assemble the actual TreeTable Currency node.
                     */
                    TreeItem(ViewerNode.CurrencyNode(sumAmount, equivSumAmount, memberStates))
                }

                /**
                 * Now that we have all nodes per currency, we sum the exchanged amounts, to be displayed in the
                 * "<currency> Equiv" column, this time on the issuer level.
                 */
                val equivAmounts = currencyNodes.map { it.value.equivAmount }.flatten()
                val equivSumAmount = reportingCurrency.bind { currency ->
                    equivAmounts.fold(Amount(0, currency), Amount<Currency>::plus)
                }

                /**
                 * Assemble the Issuer node.
                 */
                val treeItem = TreeItem(ViewerNode.IssuerNode(issuer, equivSumAmount, memberStates))

                /**
                 * Bind the children in the TreeTable structure.
                 *
                 * TODO Perhaps we shouldn't do this here, but rather have a generic way of binding nodes to the treetable once.
                 */
                treeItem.isExpanded = true
                val children: List<TreeItem<out ViewerNode.IssuerNode>> = treeItem.children
                Bindings.bindContent(children, currencyNodes)
                treeItem
            }

    /**
     * Now we build up the Observables needed for the side pane, given that the user clicks on a row.
     */
    val selectedViewerNode = cashViewerTable.singleRowSelection()

    /**
     * Holds data for a single state, to be displayed in the list in the side pane.
     */
    data class StateRow (
        val originated: LocalDateTime,
        val stateAndRef: StateAndRef<Cash.State>
    )

    /**
     * A small class describing the graphics of a single state.
     */
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

        val equivAmount: ObservableValue<out Amount<Currency>> = reportingExchange.map {
            it.second(stateRow.stateAndRef.state.data.amount.withoutIssuer())
        }

        init {
            val amountNoIssuer = stateRow.stateAndRef.state.data.amount.withoutIssuer()
            val amountFormatter = AmountFormatter.currency(AmountFormatter.comma)
            val equivFormatter = AmountFormatter.comma

            equivLabel.textProperty().bind(equivAmount.map { it.token.currencyCode.toString() })
            stateIdValueLabel.text = stateRow.stateAndRef.ref.toString()
            issuerValueLabel.text = stateRow.stateAndRef.state.data.amount.token.issuer.toString()
            originatedValueLabel.text = stateRow.originated.toString()
            amountValueLabel.text = amountFormatter.format(amountNoIssuer)
            equivValueLabel.textProperty().bind(equivAmount.map { equivFormatter.format(it) })
        }
    }

    /**
     * The list of states related to the current selection. If none or the root is selected it's empty, if an issuer or
     * currency node is selected it's the relevant states.
     */
    private val noSelectionStates = FXCollections.observableArrayList<StateAndRef<Cash.State>>()
    private val selectedViewerNodeStates = ChosenList(selectedViewerNode.map { selection ->
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

    /**
     * We re-display the exchanged sum amount, if we have a selection.
     */
    private val noSelectionSumEquiv = reportingCurrency.map { Amount(0, it) }
    private val selectedViewerNodeSumEquiv = selectedViewerNode.bind { selection ->
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

    /**
     * We add some extra timestamp data here to the selected states.
     *
     * TODO update this once we have actual timestamps.
     */
    private val stateRows = selectedViewerNodeStates.map { StateRow(LocalDateTime.now(), it) }

    /**
     * We only display the right pane if a node is selected in the TreeTable.
     */
    private val onlyLeftPaneShown = FXCollections.observableArrayList<Node>(leftPane)
    private val bothPanesShown = FXCollections.observableArrayList<Node>(leftPane, rightPane)
    private val panesShown = ChosenList<Node>(selectedViewerNode.map {
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

        totalPositionsLabel.textProperty().bind(Bindings.size(selectedViewerNodeStates).map {
            val plural = if (it == 1) "" else "s"
            "Total $it position$plural"
        })

        val equivSumLabelFormatter = AmountFormatter.currency(AmountFormatter.compact)
        equivSumLabel.textProperty().bind(selectedViewerNodeSumEquiv.map {
            equivSumLabelFormatter.format(it)
        })

        Bindings.bindContent(cashStatesList.items, stateRows)

        cashStatesList.setCustomCellFactory { StateRowGraphic(it).root }

        val cellFactory = AmountFormatter.comma.toTreeTableCellFactory<ViewerNode, Amount<Currency>>()

        // TODO use smart resize
        cashViewerTable.setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, column ->
            Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / cashViewerTable.columns.size).toInt()
        }

        cashViewerTableIssuerCurrency.setCellValueFactory {
            val node = it.value.value
            when (node) {
                ViewerNode.Root -> "".lift()
                is ViewerNode.IssuerNode -> node.issuer.toString().lift()
                is ViewerNode.CurrencyNode -> node.amount.map { it.token.toString() }
            }
        }
        cashViewerTableLocalCurrency.setCellValueFactory {
            val node = it.value.value
            when (node) {
                ViewerNode.Root -> null.lift()
                is ViewerNode.IssuerNode -> null.lift()
                is ViewerNode.CurrencyNode -> node.amount.map { it }
            }
        }
        cashViewerTableLocalCurrency.cellFactory = cellFactory
        /**
         * We must set this, otherwise on sort an exception will be thrown, as it will try to compare Amounts of differing currency
         */
        cashViewerTableLocalCurrency.isSortable = false
        cashViewerTableEquiv.setCellValueFactory {
            val node = it.value.value
            when (node) {
                ViewerNode.Root -> null.lift()
                is ViewerNode.IssuerNode -> node.sumEquivAmount.map { it }
                is ViewerNode.CurrencyNode -> node.equivAmount.map { it }
            }
        }
        cashViewerTableEquiv.cellFactory = cellFactory
        cashViewerTableEquiv.textProperty().bind(reportingCurrency.map { "$it Equiv" })

        cashViewerTable.root = TreeItem(ViewerNode.Root)
        val children: List<TreeItem<out ViewerNode>> = cashViewerTable.root.children
        Bindings.bindContent(children, cashViewerIssueNodes)

        cashViewerTable.root.isExpanded = true
        cashViewerTable.isShowRoot = false

        totalMatchingLabel.textProperty().bind(Bindings.size(cashViewerIssueNodes).map {
            val plural = if (it == 1) "" else "s"
            "Total $it matching issuer$plural"
        })
    }
}
