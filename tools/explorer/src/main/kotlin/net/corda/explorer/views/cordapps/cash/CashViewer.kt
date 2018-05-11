/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.views.cordapps.cash

import com.sun.javafx.collections.ObservableListWrapper
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import net.corda.client.jfx.model.*
import net.corda.client.jfx.utils.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.AbstractParty
import net.corda.explorer.formatters.AmountFormatter
import net.corda.explorer.formatters.PartyNameFormatter
import net.corda.explorer.identicon.identicon
import net.corda.explorer.identicon.identiconToolTip
import net.corda.explorer.model.CordaView
import net.corda.explorer.model.CordaWidget
import net.corda.explorer.model.ReportingCurrencyModel
import net.corda.explorer.model.SettingsModel
import net.corda.explorer.ui.*
import net.corda.explorer.views.*
import net.corda.finance.contracts.asset.Cash
import org.fxmisc.easybind.EasyBind
import tornadofx.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class CashViewer : CordaView("Cash") {
    // Inject UI elements.
    override val root: BorderPane by fxml()
    override val icon: FontAwesomeIcon = FontAwesomeIcon.MONEY
    // View's widget.
    override val widgets = listOf(CordaWidget("Treasury", CashWidget(), icon)).observable()
    // Left pane
    private val leftPane: VBox by fxid()
    private val splitPane: SplitPane by fxid()
    private val totalMatchingLabel: Label by fxid()
    private val cashViewerTable: TreeTableView<ViewerNode> by fxid()
    private val cashViewerTableIssuerCurrency: TreeTableColumn<ViewerNode, String> by fxid()
    private val cashViewerTableLocalCurrency: TreeTableColumn<ViewerNode, Amount<Currency>?> by fxid()
    private val cashViewerTableEquiv: TreeTableColumn<ViewerNode, Amount<Currency>?> by fxid()
    // Right pane
    private val rightPane: VBox by fxid()
    private val totalPositionsLabel: Label by fxid()
    private val cashStatesList: ListView<StateRow> by fxid()
    private val toggleButton by fxid<Button>()
    // Inject observables
    private val cashStates by observableList(ContractStateModel::cashStates)
    private val reportingCurrency by observableValue(SettingsModel::reportingCurrencyProperty)
    private val reportingExchange by observableValue(ReportingCurrencyModel::reportingExchange)

    private val selectedNode = cashViewerTable.singleRowSelection().map {
        when (it) {
            is SingleRowSelection.Selected -> it.node
            else -> null
        }
    }

    private val view = ChosenList(selectedNode.map {
        when (it) {
            null -> FXCollections.observableArrayList(leftPane)
            else -> FXCollections.observableArrayList(leftPane, rightPane)
        }
    }, "CashViewerSplitPane")

    /**
     * This holds the data for each row in the TreeTable.
     */
    sealed class ViewerNode(val equivAmount: ObservableValue<out Amount<Currency>>,
                            val states: ObservableList<StateAndRef<Cash.State>>) {
        class IssuerNode(val issuer: AbstractParty,
                         sumEquivAmount: ObservableValue<out Amount<Currency>>,
                         states: ObservableList<StateAndRef<Cash.State>>) : ViewerNode(sumEquivAmount, states)

        class CurrencyNode(val amount: ObservableValue<Amount<Currency>>,
                           equivAmount: ObservableValue<Amount<Currency>>,
                           states: ObservableList<StateAndRef<Cash.State>>) : ViewerNode(equivAmount, states)
    }

    /**
     * Holds data for a single state, to be displayed in the list in the side pane.
     */
    data class StateRow(val originated: LocalDateTime, val stateAndRef: StateAndRef<Cash.State>)

    /**
     * A small class describing the graphics of a single state.
     */
    inner class StateRowGraphic(val stateRow: StateRow) : UIComponent() {
        override val root: Parent by fxml("CashStateViewer.fxml")

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
            val amountFormatter = AmountFormatter.boring
            val equivFormatter = AmountFormatter.boring
            val resolvedIssuer: AbstractParty = stateRow.stateAndRef.resolveIssuer().value ?: stateRow.stateAndRef.state.data.amount.token.issuer.party

            stateIdValueLabel.apply {
                text = stateRow.stateAndRef.ref.toString().substring(0, 16) + "...[${stateRow.stateAndRef.ref.index}]"
                graphic = identicon(stateRow.stateAndRef.ref.txhash, 30.0)
                tooltip = identiconToolTip(stateRow.stateAndRef.ref.txhash)
            }
            equivLabel.textProperty().bind(equivAmount.map { it.token.currencyCode.toString() })
            // TODO: Anonymous should probably be italicised or similar
            issuerValueLabel.textProperty().bind(SimpleStringProperty(resolvedIssuer.nameOrNull()?.let {
                PartyNameFormatter.short.format(it)
            } ?: "Anonymous"))
            issuerValueLabel.apply { tooltip(resolvedIssuer.nameOrNull()?.let { PartyNameFormatter.full.format(it) } ?: "Anonymous") }
            originatedValueLabel.text = stateRow.originated.toString()
            amountValueLabel.text = amountFormatter.format(amountNoIssuer)
            equivValueLabel.textProperty().bind(equivAmount.map { equivFormatter.format(it) })
        }
    }

    // Wire up UI
    init {
        Bindings.bindContent(splitPane.items, view)
        /**
         * We allow filtering by both issuer and currency. We do this by filtering by both at the same time and picking the
         * one which produces more results, which seems to work, as the set of currency strings don't really overlap with
         * issuer strings.
         */
        val searchField = SearchField(cashStates,
                "Currency" to { state, text -> state.state.data.amount.token.product.toString().contains(text, true) },
                "Issuer" to { state, text -> state.resolveIssuer().value?.name?.organisation?.contains(text, true) == true }
        )
        root.top = hbox(5.0) {
            button("New Transaction", FontAwesomeIconView(FontAwesomeIcon.PLUS)) {
                setOnMouseClicked {
                    if (it.button == MouseButton.PRIMARY) {
                        find<NewTransaction>().show(this@CashViewer.root.scene.window)
                    }
                }
            }
            HBox.setHgrow(searchField.root, Priority.ALWAYS)
            add(searchField.root)
        }

        /**
         * This is where we aggregate the list of cash states into the TreeTable structure.
         */
        val cashViewerIssueNodes: ObservableList<TreeItem<out ViewerNode.IssuerNode>> =
                /**
                 * First we group the states based on the issuer. [memberStates] is all states holding currency issued by [issuer]
                 */
                AggregatedList(searchField.filteredData, { it.state.data.amount.token.issuer.party }) { issuer, memberStates ->
                    /**
                     * Next we create subgroups based on currency. [memberStates] here is all states holding currency [currency] issued by [issuer] above.
                     * Note that these states will not be displayed in the TreeTable, but rather in the side pane if the user clicks on the row.
                     */
                    val currencyNodes = AggregatedList(memberStates, { it.state.data.amount.token.product }) { currency, groupedMemberStates ->
                        /**
                         * We sum the states in the subgroup, to be displayed in the "Local Currency" column
                         */
                        val amounts = groupedMemberStates.map { it.state.data.amount.withoutIssuer() }
                        val sumAmount = amounts.foldObservable(Amount(0, currency), Amount<Currency>::plus)

                        /**
                         * We exchange the sum to the reporting currency, to be displayed in the "<currency> Equiv" column.
                         */
                        val equivSumAmount = EasyBind.combine(sumAmount, reportingExchange) { sum, exchange ->
                            exchange.second(sum)
                        }
                        /**
                         * Finally assemble the actual TreeTable Currency node.
                         */
                        TreeItem(ViewerNode.CurrencyNode(sumAmount, equivSumAmount, groupedMemberStates))
                    }

                    /**
                     * Now that we have all nodes per currency, we sum the exchanged amounts, to be displayed in the
                     * "<currency> Equiv" column, this time on the issuer level.
                     */
                    val equivAmounts = currencyNodes.map { it.value.equivAmount }.flatten()
                    val equivSumAmount = reportingCurrency.bind { currency ->
                        equivAmounts.foldObservable(Amount(0, currency), Amount<Currency>::plus)
                    }

                    /**
                     * Assemble the Issuer node.
                     */
                    val treeItem = TreeItem(ViewerNode.IssuerNode(issuer.owningKey.toKnownParty().value ?: issuer, equivSumAmount, memberStates))

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

        cashViewerTable.apply {
            root = TreeItem()
            val children: List<TreeItem<out ViewerNode>> = root.children
            Bindings.bindContent(children, cashViewerIssueNodes)
            root.isExpanded = true
            isShowRoot = false
            // TODO use smart resize
            setColumnPrefWidthPolicy { tableWidthWithoutPaddingAndBorder, _ ->
                Math.floor(tableWidthWithoutPaddingAndBorder.toDouble() / columns.size).toInt()
            }
        }
        val currencyCellFactory = AmountFormatter.boring.toTreeTableCellFactory<ViewerNode, Amount<Currency>>()

        cashViewerTableIssuerCurrency.setCellValueFactory {
            val node = it.value.value
            when (node) {
            // TODO: Anonymous should probably be italicised or similar
                is ViewerNode.IssuerNode -> SimpleStringProperty(node.issuer.nameOrNull()?.let { PartyNameFormatter.short.format(it) } ?: "Anonymous")
                is ViewerNode.CurrencyNode -> node.amount.map { it.token.toString() }
            }
        }
        cashViewerTableLocalCurrency.apply {
            setCellValueFactory {
                val node = it.value.value
                when (node) {
                    is ViewerNode.IssuerNode -> null.lift()
                    is ViewerNode.CurrencyNode -> node.amount.map { it }
                }
            }
            cellFactory = currencyCellFactory
            /**
             * We must set this, otherwise on sort an exception will be thrown, as it will try to compare Amounts of differing currency
             */
            isSortable = false
        }

        cashViewerTableEquiv.apply {
            setCellValueFactory {
                it.value.value.equivAmount.map { it }
            }
            cellFactory = currencyCellFactory
            textProperty().bind(reportingCurrency.map { "$it Equiv" })
        }

        // Right Pane.
        totalPositionsLabel.textProperty().bind(cashStatesList.itemsProperty().map {
            val plural = if (it.size == 1) "" else "s"
            "Total ${it.size} position$plural"
        })

        cashStatesList.apply {
            // TODO update this once we have actual timestamps.
            itemsProperty().bind(selectedNode.map { it?.states?.map { StateRow(LocalDateTime.now(), it) } ?: ObservableListWrapper(emptyList()) })
            setCustomCellFactory { StateRowGraphic(it).root }
        }

        // TODO Think about i18n!
        totalMatchingLabel.textProperty().bind(Bindings.size(cashViewerIssueNodes).map {
            val plural = if (it == 1) "" else "s"
            "Total $it matching issuer$plural"
        })

        toggleButton.setOnAction {
            cashViewerTable.selectionModel.clearSelection()
        }
    }

    private class CashWidget : VBox() {
        // Inject data.
        private val reportingCurrency by observableValue(SettingsModel::reportingCurrencyProperty)
        private val cashStates by observableList(ContractStateModel::cashStates)
        private val exchangeRate: ObservableValue<ExchangeRate> by observableValue(ExchangeRateModel::exchangeRate)
        private val sumAmount = AmountBindings.sumAmountExchange(
                cashStates.map { it.state.data.amount.withoutIssuer() },
                reportingCurrency,
                exchangeRate)

        init {
            padding = Insets(0.0, 10.0, 0.0, 0.0)
            val xAxis = NumberAxis().apply {
                //isAutoRanging = true
                isMinorTickVisible = false
                isForceZeroInRange = false
                tickLabelFormatter = stringConverter {
                    Instant.ofEpochMilli(it.toLong()).atZone(TimeZone.getDefault().toZoneId()).toLocalTime().toString()
                }
            }
            val yAxis = NumberAxis().apply {
                isAutoRanging = true
                isMinorTickVisible = false
                isForceZeroInRange = false
                tickLabelFormatter = stringConverter { it.toStringWithSuffix() }
            }
            linechart(null, xAxis, yAxis) {
                series("USD") {
                    sumAmount.addListener { _, _, _ ->
                        val lastAmount = data.last().value?.yValue
                        val currAmount = sumAmount.value.toDecimal()
                        val lastTimeStamp = data.last().value?.xValue
                        val currentTimeStamp = System.currentTimeMillis()

                        // If amount is not the same - always add a data point.
                        if (lastAmount == null || lastAmount != currAmount) {
                            // If update arrived in very close succession to the previous one - kill the last point received to eliminate un-necessary noise on the graph.
                            if(lastTimeStamp != null && currentTimeStamp - lastTimeStamp.toLong() < 1.seconds.toMillis()) {
                                data.safelyTransition {
                                    remove(size - 1, size)
                                }
                            }

                            // Add a new data point.
                            data(currentTimeStamp, currAmount)

                            // Limit population of data points to make graph painting faster.
                            data.safelyTransition {
                                if (size > 300) remove(0, 1)
                            }
                        }
                    }
                }
                createSymbols = false
                animated = false
            }
        }

        private fun <X, Y> ObservableList<XYChart.Data<X, Y>>.safelyTransition(block: ObservableList<XYChart.Data<X, Y>>.() -> Unit) {
            runInFxApplicationThread {
                // Modify data in UI thread to properly propagate to GUI.
                this.block()
            }
        }
    }
}
