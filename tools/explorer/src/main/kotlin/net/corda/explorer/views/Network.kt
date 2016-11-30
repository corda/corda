package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ContentDisplay
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import net.corda.client.fxutils.concatenate
import net.corda.client.fxutils.last
import net.corda.client.fxutils.map
import net.corda.client.fxutils.sequence
import net.corda.client.model.*
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.node.NodeInfo
import net.corda.explorer.model.CordaView
import tornadofx.*

// TODO : Construct a node map using node info and display them on a world map.
// TODO : Allow user to see transactions between nodes on a world map.
class Network : CordaView() {
    override val root by fxml<Parent>()
    override val icon = FontAwesomeIcon.GLOBE

    // Inject data.
    val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    val notaries by observableList(NetworkIdentityModel::notaries)
    val peers by observableList(NetworkIdentityModel::parties)
    val transactions by observableList(GatheredTransactionDataModel::partiallyResolvedTransactions)

    // Components
    private val myIdentityPane by fxid<BorderPane>()
    private val notaryList by fxid<VBox>()
    private val peerList by fxid<VBox>()
    private val mapScrollPane by fxid<ScrollPane>()
    private val mapPane by fxid<Pane>()

    // Create a strong ref to prevent GC.
    private val notaryButtons = notaries.map { it.render() }
    private val peerButtons = peers.filtered { it != myIdentity.value }.map { it.render() }
    private val myCoordinate = Bindings.createObjectBinding({
        myIdentity.value?.physicalLocation?.coordinate?.project(mapPane.width, mapPane.height, 85.0511, -85.0511, -180.0, 180.0)?.let {
            Pair(it.first, it.second)
        } ?: Pair(0.0, 0.0)
    }, arrayOf(mapPane.widthProperty(), mapPane.heightProperty(), myIdentity))

    private val nodesOnMap = FXCollections.observableArrayList(notaries, peers).concatenate().map { node ->
        label {
            graphic = FontAwesomeIconView(FontAwesomeIcon.DOT_CIRCLE_ALT)
            contentDisplay = ContentDisplay.TOP
            text = node.legalIdentity.name
            val coordinate = Bindings.createObjectBinding({
                node.physicalLocation?.coordinate?.project(mapPane.width, mapPane.height, 85.0511, -85.0511, -180.0, 180.0)?.let {
                    Pair(it.first, it.second)
                } ?: Pair(0.0, 0.0)
            }, arrayOf(mapPane.widthProperty(), mapPane.heightProperty()))

            layoutXProperty().bind(widthProperty().map { coordinate.value.first - it.toDouble() / 2 })
            layoutYProperty().bind(heightProperty().map { coordinate.value.second - it.toDouble() / 4 })
        }
    }

    private fun NodeInfo.render(): Node {
        return button {
            graphic = vbox {
                label(this@render.legalIdentity.name) {
                    font = Font.font(font.family, FontWeight.BOLD, 15.0)
                }
                gridpane {
                    hgap = 5.0
                    vgap = 5.0
                    row("Pub Key :") {
                        copyableLabel(SimpleObjectProperty(this@render.legalIdentity.owningKey.toBase58String()))
                    }
                    row("Services :") {
                        label(this@render.advertisedServices.map { it.info }.joinToString(", "))
                    }
                    this@render.physicalLocation?.apply {
                        row("Location :") {
                            label(this@apply.description)
                        }
                    }
                }
            }
        }
    }

    init {
        myIdentityPane.centerProperty().bind(myIdentity.map { it?.render() })
        Bindings.bindContent(notaryList.children, notaryButtons)
        Bindings.bindContent(peerList.children, peerButtons)

        Bindings.bindContent(mapPane.children, nodesOnMap)

        val scroll = myCoordinate.map {
            val width = mapPane.boundsInLocal.width
            val height = mapPane.boundsInLocal.height
            Pair(it.first / width, it.second / height)
        }

        mapScrollPane.vvalueProperty().bind(scroll.map { it.second })
        mapScrollPane.hvalueProperty().bind(scroll.map { it.first })

        val transactions = transactions.map {
            val inputParties = it.inputs.sequence()
                    .map { it as? PartiallyResolvedTransaction.InputResolution.Resolved }
                    .filterNotNull()
                    .map { it.stateAndRef }.getParties()
            val outputParties = it.transaction.tx.outputs
                    .mapIndexed { index, transactionState ->
                        val stateRef = StateRef(it.id, index)
                        StateAndRef(transactionState, stateRef)
                    }.observable().getParties()
            val signingParties = it.transaction.sigs.map { getModel<NetworkIdentityModel>().lookup(it.by) }
            (inputParties + outputParties + signingParties).map { it.value }.filterNotNull().toSet()
        }.last()

    }

    private fun List<StateAndRef<ContractState>>.getParties() = map { it.state.data.participants.map { getModel<NetworkIdentityModel>().lookup(it) } }.flatten()

}