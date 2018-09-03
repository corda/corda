package net.corda.explorer.views.cordapps.iou

import com.google.common.base.Splitter
import com.sun.javafx.collections.ImmutableObservableList
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.VPos
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Window
import net.corda.client.jfx.model.NetworkIdentityModel
import net.corda.client.jfx.model.NodeMonitorModel
import net.corda.client.jfx.model.observableList
import net.corda.client.jfx.model.observableValue
import net.corda.client.jfx.utils.isNotNull
import net.corda.client.jfx.utils.map
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.explorer.formatters.PartyNameFormatter
import net.corda.explorer.model.MembershipListModel
import net.corda.explorer.views.bigDecimalFormatter
import net.corda.explorer.views.stringConverter
import net.corda.sample.businessnetwork.iou.IOUFlow
import net.corda.testing.core.singleIdentityAndCert
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*

class NewTransaction : Fragment() {
    override val root by fxml<DialogPane>()
    // Components
    private val partyATextField by fxid<TextField>()
    private val partyBChoiceBox by fxid<ChoiceBox<PartyAndCertificate>>()
    private val amountTextField by fxid<TextField>()
    // Inject data
    private val parties by observableList(NetworkIdentityModel::parties)
    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val notaries by observableList(NetworkIdentityModel::notaries)
    private val executeButton = ButtonType("Execute", ButtonBar.ButtonData.APPLY)

    fun show(window: Window) {

        // Every time re-query from the server side
        val elementsFromServer = try {
            MembershipListModel().allParties
        } catch (ex: Exception) {
            loggerFor<NewTransaction>().error("Unexpected error fetching membership list content", ex)
            ImmutableObservableList<AbstractParty>()
        }

        partyBChoiceBox.apply {
            items = FXCollections.observableList(parties.map { it.singleIdentityAndCert() }).filtered { elementsFromServer.contains(it.party) }.sorted()
        }

        newTransactionDialog(window).showAndWait().ifPresent { request ->
            val dialog = Alert(Alert.AlertType.INFORMATION).apply {
                headerText = null
                contentText = "Transaction Started."
                dialogPane.isDisable = true
                initOwner(window)
                show()
            }
            val handle: FlowHandle<SignedTransaction> = rpcProxy.value!!.cordaRPCOps.startFlow(::IOUFlow, request.first, request.second)
            runAsync {
                try {
                    handle.returnValue.getOrThrow()
                } finally {
                    dialog.dialogPane.isDisable = false
                }
            }.ui {
                val stx: SignedTransaction = it
                val type = "IOU posting completed successfully"
                dialog.alertType = Alert.AlertType.INFORMATION
                dialog.dialogPane.content = gridpane {
                    padding = Insets(10.0, 40.0, 10.0, 20.0)
                    vgap = 10.0
                    hgap = 10.0
                    row { label(type) { font = Font.font(font.family, FontWeight.EXTRA_BOLD, font.size + 2) } }
                    row {
                        label("Transaction ID :") { GridPane.setValignment(this, VPos.TOP) }
                        label { text = Splitter.fixedLength(16).split("${stx.id}").joinToString("\n") }
                    }
                }
                dialog.dialogPane.scene.window.sizeToScene()
            }.setOnFailed {
                val ex = it.source.exception
                when (ex) {
                    is FlowException -> {
                        dialog.alertType = Alert.AlertType.ERROR
                        dialog.contentText = ex.message
                    }
                    else -> {
                        dialog.close()
                        ExceptionDialog(ex).apply { initOwner(window) }.showAndWait()
                    }
                }
            }
        }
    }

    private fun newTransactionDialog(window: Window) = Dialog<Pair<Int, Party>>().apply {
        dialogPane = root
        initOwner(window)
        setResultConverter {
            when (it) {
                executeButton -> Pair(amountTextField.text.toInt(), partyBChoiceBox.value.party)
                else -> null
            }
        }
    }

    init {
        // Disable everything when not connected to node.
        val notariesNotNullBinding = Bindings.createBooleanBinding({ notaries.isNotEmpty() }, arrayOf(notaries))
        val enableProperty = myIdentity.isNotNull().and(rpcProxy.isNotNull()).and(notariesNotNullBinding)
        root.disableProperty().bind(enableProperty.not())

        // Party A text field always display my identity name, not editable.
        partyATextField.textProperty().bind(myIdentity.map { it?.let { PartyNameFormatter.short.format(it.name) } ?: "" })

        // Party B
        partyBChoiceBox.apply {
            converter = stringConverter { it?.let { PartyNameFormatter.short.format(it.name) } ?: "" }
        }
        // Amount
        amountTextField.textFormatter = bigDecimalFormatter()

        // Validate inputs.
        val formValidCondition = arrayOf(
                myIdentity.isNotNull(),
                partyBChoiceBox.visibleProperty().not().or(partyBChoiceBox.valueProperty().isNotNull),
                amountTextField.textProperty().isNotEmpty
        ).reduce(BooleanBinding::and)

        // Enable execute button when form is valid.
        root.buttonTypes.add(executeButton)
        root.lookupButton(executeButton).disableProperty().bind(formValidCondition.not())
    }
}