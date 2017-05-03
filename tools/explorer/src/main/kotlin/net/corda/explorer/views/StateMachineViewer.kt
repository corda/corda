package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.collections.ObservableList
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TabPane
import javafx.scene.control.TableView
import javafx.scene.control.TitledPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.scene.text.FontWeight
import javafx.scene.text.TextAlignment
import net.corda.client.jfx.model.StateMachineData
import net.corda.client.jfx.model.StateMachineDataModel
import net.corda.client.jfx.model.StateMachineStatus
import net.corda.client.jfx.model.observableList
import net.corda.client.jfx.model.observableListReadOnly
import net.corda.client.jfx.utils.map
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toBase58String
import net.corda.core.flows.FlowInitiator
import net.corda.core.transactions.SignedTransaction
import net.corda.explorer.formatters.FlowInitiatorFormatter
import net.corda.explorer.formatters.FlowNameFormatter
import net.corda.explorer.identicon.identicon
import net.corda.explorer.identicon.identiconToolTip
import net.corda.explorer.model.CordaView
import net.corda.explorer.model.CordaWidget
import net.corda.explorer.ui.setCustomCellFactory
import tornadofx.*

// TODO Rethink whole idea of showing communication as table, it should be tree view for each StateMachine (with subflows and other communication)
class StateMachineViewer : CordaView("Flow Triage") {
    override val root by fxml<TabPane>()
    override val icon = FontAwesomeIcon.HEARTBEAT
    override val widgets = listOf(CordaWidget(title, StateMachineViewer.StateMachineWidget())).observable()
    private val progressViewTable by fxid<TableView<StateMachineData>>()
    private val doneViewTable by fxid<TableView<StateMachineData>>()
    private val errorViewTable by fxid<TableView<StateMachineData>>()

    private class StateMachineWidget : BorderPane() {
        private val flows by observableListReadOnly(StateMachineDataModel::stateMachinesInProgress)

        // TODO can add stats: in progress, errored, maybe done to the widget?
        init {
            right {
                label {
                    textProperty().bind(Bindings.size(flows).map(Number::toString))
                    BorderPane.setAlignment(this, Pos.BOTTOM_RIGHT)
                }
            }
        }
    }

    private val stateMachinesInProgress by observableList(StateMachineDataModel::stateMachinesInProgress)
    private val stateMachinesFinished by observableList(StateMachineDataModel::stateMachinesFinished)
    private val stateMachinesError by observableList(StateMachineDataModel::stateMachinesError)

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
            column("ID", StateMachineData::id) { // TODO kill that ID column
                minWidth = 100.0
                maxWidth = 200.0
            }.setCustomCellFactory {
                label("$it") {
                    val hash = SecureHash.sha256(it.toString())
                    graphic = identicon(hash, 15.0)
                    tooltip = identiconToolTip(hash) //TODO Have id instead of hash.
                }
            }
            column("Flow name", StateMachineData::stateMachineName).cellFormat { text = FlowNameFormatter.boring.format(it) }
            column("Initiator", StateMachineData::flowInitiator).cellFormat { text = FlowInitiatorFormatter.format(it) }
            column("Flow Status", StateMachineData::stateMachineStatus).cellFormat {
                if (it == null)
                    text = "No progress data"
                else text = it.status
            } // TODO null
            column("Result", StateMachineData::addRmStatus).setCustomCellFactory {
                if (it is StateMachineStatus.Removed) {
                    if (it.result.error == null) {
                        label("Success") {
                            graphic = FontAwesomeIconView(FontAwesomeIcon.CHECK).apply {
                                glyphSize = 15.0
                                textAlignment = TextAlignment.CENTER
                                style = "-fx-fill: green"
                            }
                        }
                    } else {
                        label("Error") {
                            graphic = FontAwesomeIconView(FontAwesomeIcon.BOLT).apply {
                                glyphSize = 15.0
                                textAlignment = TextAlignment.CENTER
                                style = "-fx-fill: -color-4"
                            }
                        }
                    }
                }
                else {
                    label("In progress") {
                        // TODO Other icons: spnner, hourglass-half, hourglass-1, send-o, space-shuttle ;)
                        graphic = FontAwesomeIconView(FontAwesomeIcon.ROCKET).apply {
                            glyphSize = 15.0
                            textAlignment = TextAlignment.CENTER
                            style = "-fx-fill: lightslategrey"
                        }
                    }
                }
            }
        }
    }

    init {
        makeColumns(progressViewTable, stateMachinesInProgress, false)
        makeColumns(doneViewTable, stateMachinesFinished)
        makeColumns(errorViewTable, stateMachinesError)
    }

    private inner class StateMachineDetailsView(val smmData: StateMachineData) : Fragment() {
        override val root by fxml<Parent>()
        private val flowNamePane by fxid<TitledPane>()
        private val flowProgressPane by fxid<TitledPane>()
        private val flowInitiatorPane by fxid<TitledPane>()
        private val flowResultPane by fxid<TitledPane>()

        init {
            flowNamePane.apply {
                content = label {
                    text = FlowNameFormatter.boring.format(smmData.stateMachineName)
                }
            }
            flowProgressPane.apply {
                content = label {
                    text = smmData.stateMachineStatus.value?.status // TODO later we can do some magic with showing progress steps with subflows
                }
            }
            flowInitiatorPane.apply {
                //TODO use fxml to access initiatorGridPane
                // initiatorGridPane.apply {when...
                content = when (smmData.flowInitiator) {
                    is FlowInitiator.Shell -> ShellNode() // TODO Extend this when we will have more information on shell user.
                    is FlowInitiator.Peer -> PeerNode(smmData.flowInitiator as FlowInitiator.Peer)
                    is FlowInitiator.RPC -> RPCNode(smmData.flowInitiator as FlowInitiator.RPC)
                    is FlowInitiator.Scheduled -> ScheduledNode(smmData.flowInitiator as FlowInitiator.Scheduled)
                }
            }
            flowResultPane.apply {
                val status = smmData.addRmStatus.value
                if (status is StateMachineStatus.Removed) {
                    content = status.result.match(onValue =  { ResultNode(it) }, onError = { ErrorNode(it) })
                }
            }
        }
    }

    // TODO make that Vbox part of FXML
    private inner class ResultNode<T>(result: T) : VBox() {
        init {
            spacing = 10.0
            padding = Insets(5.0, 5.0, 5.0, 5.0)
            if (result == null) {
                label("No return value from flow.")
            } else if (result is SignedTransaction) {
//                scrollpane {
//                    hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
//                    vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                    // TODO Make link to transaction view
                    label("Signed transaction")
                    label {
                        text = result.id.toString()
                        graphic = identicon(result.id, 30.0)
                        tooltip = identiconToolTip(result.id)
                    }
//                }
            } else if (result is Unit) {
                label("Flow completed with success.")
            }
            else {
                // TODO Here we could have sth different than SignedTransaction
                label(result.toString())
            }
        }
    }

    // TODO make that Vbox part of FXML
    private inner class ErrorNode(val error: Throwable) : VBox() {
        init {
            vbox {
                spacing = 10.0
                padding = Insets(5.0, 5.0, 5.0, 5.0)
                label("Error") {
                    graphic = FontAwesomeIconView(FontAwesomeIcon.BOLT).apply {
                        glyphSize = 30
                        textAlignment = TextAlignment.CENTER
                        style = "-fx-fill: -color-4"
                    }
                }
                // TODO think of border styling
                vbox {
                    spacing = 10.0
                    label { text = error::class.simpleName }
                    scrollpane {
                        hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
                        vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                        label { text = error.message }
                    }
                }
            }

        }
    }

    private inner class ShellNode : Label() {
        init {
            label("Flow started by shell user")
        }
    }

    // TODO make it more generic, reuse gridpane definition - to fxml
    private inner class PeerNode(val initiator: FlowInitiator.Peer): GridPane() {
        init {
            gridpane {
                padding = Insets(0.0, 5.0, 10.0, 10.0)
                vgap = 10.0
                hgap = 10.0
                row {
                    label("Flow started by a peer node") {
                        gridpaneConstraints {
                            columnSpan = 2
                            hAlignment = HPos.CENTER
                        }
                    }
                }
//                scrollpane { // TODO scrollbar vbox + hbox
//                    hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
//                    vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                row {
                    label("Legal name: ") {
                        gridpaneConstraints { hAlignment = HPos.LEFT }
                        style { fontWeight = FontWeight.BOLD }
                        minWidth = 150.0
                        prefWidth = 150.0
                    }
                    label(initiator.party.name) { gridpaneConstraints { hAlignment = HPos.LEFT } }
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
    }

    private inner class RPCNode(val initiator: FlowInitiator.RPC) : GridPane() {
        init {
            gridpane {
                padding = Insets(0.0, 5.0, 10.0, 10.0)
                vgap = 10.0
                hgap = 10.0
                row {
                    label("Flow started by a RPC user") {
                        gridpaneConstraints {
                            columnSpan = 2
                            hAlignment = HPos.CENTER
                        }
                    }
                }
                row {
                    label("User name: ") {
                        gridpaneConstraints { hAlignment = HPos.LEFT }
                        style { fontWeight = FontWeight.BOLD }
                        prefWidth = 150.0
                    }
                    label(initiator.username) { gridpaneConstraints { hAlignment = HPos.LEFT } }
                }
            }
        }
    }

    // TODO test
    private inner class ScheduledNode(val initiator: FlowInitiator.Scheduled) : GridPane() {
        init {
            gridpane {
                padding = Insets(0.0, 5.0, 10.0, 10.0)
                vgap = 10.0
                hgap = 10.0
                row {
                    label("Flow started as scheduled activity")
                    gridpaneConstraints {
                        columnSpan = 2
                        hAlignment = HPos.CENTER
                    }
                }
                row {
                    label("Scheduled state: ") {
                        gridpaneConstraints { hAlignment = HPos.LEFT }
                        style { fontWeight = FontWeight.BOLD }
                        prefWidth = 150.0
                    }
                    label(initiator.scheduledState.ref.toString()) { gridpaneConstraints { hAlignment = HPos.LEFT } } //TODO format
                }
                row {
                    label("Scheduled at: ") {
                        gridpaneConstraints { hAlignment = HPos.LEFT }
                        style { fontWeight = FontWeight.BOLD }
                        prefWidth = 150.0
                    }
                    label(initiator.scheduledState.scheduledAt.toString()) { gridpaneConstraints { hAlignment = HPos.LEFT } } //TODO format
                }
            }
        }
    }
}
