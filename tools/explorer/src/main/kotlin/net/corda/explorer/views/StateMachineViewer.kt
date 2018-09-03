package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.collections.ObservableList
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.TableView
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.scene.text.FontWeight
import javafx.scene.text.TextAlignment
import net.corda.client.jfx.model.*
import net.corda.client.jfx.utils.map
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInitiator
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Try
import net.corda.core.utilities.toBase58String
import net.corda.explorer.formatters.FlowInitiatorFormatter
import net.corda.explorer.formatters.FlowNameFormatter
import net.corda.explorer.formatters.PartyNameFormatter
import net.corda.explorer.identicon.identicon
import net.corda.explorer.identicon.identiconToolTip
import net.corda.explorer.model.CordaView
import net.corda.explorer.model.CordaViewModel
import net.corda.explorer.model.CordaWidget
import net.corda.explorer.ui.setCustomCellFactory
import tornadofx.*


// TODO Rethink whole idea of showing communication as table, it should be tree view for each StateMachine (with subflows).
class StateMachineViewer : CordaView("Flow Triage") {
    override val root by fxml<BorderPane>()
    override val icon = FontAwesomeIcon.HEARTBEAT
    override val widgets = listOf(CordaWidget(title, StateMachineWidget(), icon)).observable()
    private val allViewTable by fxid<TableView<StateMachineData>>()
    private val matchingFlowsLabel by fxid<Label>()
    private val selectedView by writableValue(CordaViewModel::selectedView)


    private val stateMachinesAll by observableList(StateMachineDataModel::stateMachinesAll)

    inner private class StateMachineWidget : GridPane() {
        private val error by observableValue(StateMachineDataModel::error)
        private val success by observableValue(StateMachineDataModel::success)
        private val progress by observableValue(StateMachineDataModel::progress)

        init {
            padding = Insets(0.0, 5.0, 10.0, 10.0)
            hgap = 5.0
            styleClass += "chart-plot-background"
            row {
                add(makeIconLabel(FontAwesomeIcon.CHECK, "", "-fx-fill: lightslategrey", 30.0))
                label { textProperty().bind(success.map(Number::toString)) }
            }
            row {
                add(makeIconLabel(FontAwesomeIcon.BOLT, "", "-fx-fill: lightslategrey", 30.0))
                label { textProperty().bind(error.map(Number::toString)) }
            }
            row {
                add(makeIconLabel(FontAwesomeIcon.ROCKET, "", "-fx-fill: lightslategrey", 30.0))
                label { textProperty().bind(progress.map(Number::toString)) }
            }
        }
    }

    fun makeIconLabel(icon: FontAwesomeIcon, initText: String, customStyle: String? = null, iconSize: Double = 15.0): Label {
        return label {
            graphic = FontAwesomeIconView(icon).apply {
                glyphSize = iconSize
                textAlignment = TextAlignment.LEFT
                style = customStyle
            }
            text = initText
            gridpaneConstraints { hAlignment = HPos.CENTER }
        }
    }

    fun makeColumns(table: TableView<StateMachineData>, tableItems: ObservableList<StateMachineData>, withResult: Boolean = true) {
        table.apply {
            items = tableItems
            if (withResult) {
                rowExpander(expandOnDoubleClick = true) {
                    add(StateMachineDetailsView(it).root)
                }.apply {
                    // Column stays the same size, but we don't violate column restricted resize policy for the whole table view.
                    minWidth = 26.0
                    maxWidth = 26.0
                }
            }
            // TODO Kill that ID column or replace it with something useful when we will have flow audit utilities.
            //  For now it's rather for visual purpose, so you can observe flow.
            column("ID", StateMachineData::id) {
                minWidth = 100.0
                maxWidth = 200.0
            }.setCustomCellFactory {
                val toDisplay = it.toString().removeSurrounding("[", "]")
                label(toDisplay) {
                    val hash = SecureHash.sha256(it.toString())
                    graphic = identicon(hash, 15.0)
                    tooltip = identiconToolTip(hash, toDisplay)
                }
            }
            column("Flow name", StateMachineData::stateMachineName).cellFormat { text = FlowNameFormatter.camelCase.format(it) }
            column("Initiator", StateMachineData::flowInitiator).setCustomCellFactory {
                val (initIcon, initText) = FlowInitiatorFormatter.withIcon(it)
                makeIconLabel(initIcon, initText, "-fx-fill: lightgray")
            }
            column("Flow Status", StateMachineData::smmStatus).setCustomCellFactory {
                val addRm = it.first.value
                val progress = it.second.value.status ?: "No progress data"
                if (addRm is StateMachineStatus.Removed) {
                    if (addRm.result is Try.Success) {
                        makeIconLabel(FontAwesomeIcon.CHECK, "Success", "-fx-fill: green")
                    } else {
                        makeIconLabel(FontAwesomeIcon.BOLT, progress, "-fx-fill: -color-4")
                    }
                } else {
                    makeIconLabel(FontAwesomeIcon.ROCKET, progress, "-fx-fill: lightslategrey")
                }
            }
        }
    }

    init {
        val searchField = SearchField(stateMachinesAll,
                "Flow name" to { sm, s -> sm.stateMachineName.contains(s, true) },
                "Initiator" to { sm, s -> FlowInitiatorFormatter.format(sm.flowInitiator).contains(s, true) },
                "Flow Status" to { sm, s ->
                    val stat = sm.smmStatus.second.value?.status ?: "No progress data"
                    stat.contains(s, true)
                },
                "Error" to { sm, _ ->
                    val smAddRm = sm.smmStatus.first.value
                    if (smAddRm is StateMachineStatus.Removed)
                        smAddRm.result is Try.Failure
                    else false
                },
                "Done" to { sm, _ ->
                    val smAddRm = sm.smmStatus.first.value
                    if (smAddRm is StateMachineStatus.Removed)
                        smAddRm.result is Try.Success
                    else false
                },
                "In progress" to { sm, _ -> sm.smmStatus.first.value !is StateMachineStatus.Removed },
                disabledFields = listOf("Error", "Done", "In progress")
        )
        root.top = searchField.root
        makeColumns(allViewTable, searchField.filteredData)
        matchingFlowsLabel.textProperty().bind(Bindings.size(allViewTable.items).map {
            "$it matching flow${if (it == 1) "" else "s"}"
        })
    }

    private inner class StateMachineDetailsView(smmData: StateMachineData) : Fragment() {
        override val root by fxml<Parent>()
        private val flowInitiatorGrid by fxid<GridPane>()
        private val flowResultVBox by fxid<VBox>()

        init {
            //TODO It would be nice to have flow graph with showing progress steps with subflows + timestamps (left it for second iteration).
            when (smmData.flowInitiator) {
                is FlowInitiator.Shell -> makeShellGrid(flowInitiatorGrid) // TODO Extend this when we will have more information on shell user.
                is FlowInitiator.Peer -> makePeerGrid(flowInitiatorGrid, smmData.flowInitiator as FlowInitiator.Peer)
                is FlowInitiator.RPC -> makeRPCGrid(flowInitiatorGrid, smmData.flowInitiator as FlowInitiator.RPC)
                is FlowInitiator.Scheduled -> makeScheduledGrid(flowInitiatorGrid, smmData.flowInitiator as FlowInitiator.Scheduled)
            }
            val status = smmData.smmStatus.first.value
            if (status is StateMachineStatus.Removed) {
                val result = status.result
                when (result) {
                    is Try.Success -> makeResultVBox(flowResultVBox, result.value)
                    is Try.Failure -> makeErrorVBox(flowResultVBox, result.exception)
                }
            }
        }
    }

    private fun <T> makeResultVBox(vbox: VBox, result: T) {
        if (result is SignedTransaction) {
            vbox.apply {
                label("Signed transaction").apply { style { fontWeight = FontWeight.BOLD } }
                label {
                    style = "-fx-cursor: hand;"
                    setOnMouseClicked {
                        if (it.button == MouseButton.PRIMARY) {
                            selectedView.value = tornadofx.find<TransactionViewer>().apply { txIdToScroll = result.id }
                        }
                    }
                    text = result.id.toString()
                    graphic = identicon(result.id, 30.0)
                    tooltip = identiconToolTip(result.id)
                }

            }
        } else if (result != null && result !is Unit) {
            // TODO Here we could have sth different than SignedTransaction/Unit
            vbox.apply {
                label("Flow completed with success. Result: ").apply { style { fontWeight = FontWeight.BOLD } }
                label(result.toString())
            }
        }
    }

    private fun makeErrorVBox(vbox: VBox, error: Throwable) {
        vbox.apply {
            label {
                text = error::class.simpleName
                graphic = FontAwesomeIconView(FontAwesomeIcon.BOLT).apply {
                    glyphSize = 30
                    textAlignment = TextAlignment.CENTER
                    style = "-fx-fill: -color-4"
                }
            }
            label { text = error.message }
        }
    }

    private fun makeShellGrid(gridPane: GridPane) {
        gridPane.apply {
            label("Flow started by shell user")
        }
    }

    private fun makePeerGrid(gridPane: GridPane, initiator: FlowInitiator.Peer) {
        gridPane.apply {
            style = "-fx-cursor: hand;"
            setOnMouseClicked {
                if (it.button == MouseButton.PRIMARY) {
                    val short = PartyNameFormatter.short.format(initiator.party.name)
                    selectedView.value = tornadofx.find<Network>().apply { centralPeer = short}
                }
            }
            row {
                label("Peer legal name: ") {
                    gridpaneConstraints { hAlignment = HPos.LEFT }
                    style { fontWeight = FontWeight.BOLD }
                    minWidth = 150.0
                    prefWidth = 150.0
                }
                label(initiator.party.name.toString()) { gridpaneConstraints { hAlignment = HPos.LEFT } }
            }
            row {
                label("Owning key: ") {
                    gridpaneConstraints { hAlignment = HPos.LEFT }
                    style { fontWeight = FontWeight.BOLD }
                    minWidth = 150.0
                    prefWidth = 150.0
                }
                label(initiator.party.owningKey.toBase58String()) { gridpaneConstraints { hAlignment = HPos.LEFT } }
            }
        }
    }

    private fun makeRPCGrid(gridPane: GridPane, initiator: FlowInitiator.RPC) {
        gridPane.apply {
            row {
                label("RPC user name: ") {
                    gridpaneConstraints { hAlignment = HPos.LEFT }
                    style { fontWeight = FontWeight.BOLD }
                    prefWidth = 150.0
                }
                label(initiator.username) { gridpaneConstraints { hAlignment = HPos.LEFT } }
            }
        }
    }

    private fun makeScheduledGrid(gridPane: GridPane, initiator: FlowInitiator.Scheduled) {
        gridPane.apply {
            row {
                label("Scheduled state: ") {
                    gridpaneConstraints { hAlignment = HPos.LEFT }
                    style { fontWeight = FontWeight.BOLD }
                    prefWidth = 150.0
                }
                label(initiator.scheduledState.ref.toString()) { gridpaneConstraints { hAlignment = HPos.LEFT } }
            }
            row {
                label("Scheduled at: ") {
                    gridpaneConstraints { hAlignment = HPos.LEFT }
                    style { fontWeight = FontWeight.BOLD }
                    prefWidth = 150.0
                }
                label(initiator.scheduledState.scheduledAt.toString()) { gridpaneConstraints { hAlignment = HPos.LEFT } }
            }
        }
    }
}
