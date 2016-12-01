package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import net.corda.client.fxutils.*
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
    private val mapImageView by fxid<ImageView>()
    private val mapOriginalHeight = SimpleDoubleProperty()
    private val zoomInButton by fxid<Button>()
    private val zoomOutButton by fxid<Button>()

    // Node observables, declare here to create a strong ref to prevent GC, which removes listener from observables.
    private val notaryComponents = notaries.map { it.render() }
    private val notaryButtons = notaryComponents.map { it.button }
    private val peerComponents = peers.map { it.render() }
    private val peerButtons = peerComponents.filtered { it.nodeInfo != myIdentity.value }.map { it.button }

    private val myComponent = myIdentity.map { it?.render() }
    private val myButton = myComponent.map { it?.button }
    private val myMapLabel = myComponent.map { it?.label }

    private data class MapViewComponents(val nodeInfo: NodeInfo, val button: Button, val label: Label)

    private val nodesOnMap = FXCollections.observableArrayList(notaryComponents.map { it.label }, peerComponents.map { it.label }).concatenate()

    private fun NodeInfo.render(): MapViewComponents {
        val node = this
        val mapLabel = label {
            graphic = FontAwesomeIconView(FontAwesomeIcon.DOT_CIRCLE_ALT)
            contentDisplay = ContentDisplay.TOP
            text = node.legalIdentity.name
            val coordinate = Bindings.createObjectBinding({
                node.physicalLocation?.coordinate?.project(mapPane.width, mapPane.height, 85.0511, -85.0511, -180.0, 180.0)?.let {
                    Pair(it.first, it.second)
                } ?: Pair(0.0, 0.0)
            }, arrayOf(mapPane.widthProperty(), mapPane.heightProperty()))
            layoutXProperty().bind(coordinate.map { it.first - width / 2 })
            layoutYProperty().bind(coordinate.map { it.second - height / 4 })
        }

        val button = button {
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
            setOnMouseClicked {
                mapScrollPane.centerLabel(mapLabel)
            }
        }
        return MapViewComponents(this, button, mapLabel)
    }

    init {
        myIdentityPane.centerProperty().bind(myButton)
        Bindings.bindContent(notaryList.children, notaryButtons)
        Bindings.bindContent(peerList.children, peerButtons)
        Bindings.bindContent(mapPane.children, nodesOnMap)

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

        // Run once when the screen is layout.
        mapPane.heightProperty().addListener { _o, old, _new ->
            if (old == 0.0) {
                mapOriginalHeight.value = mapImageView.prefHeight(-1.0)
                myMapLabel.value?.let { mapScrollPane.centerLabel(it) }
            }
        }

        mapPane.setOnZoom {
            zoom(it.zoomFactor, Point2D(it.x, it.y))
        }

        zoomInButton.setOnAction { zoom(1.2) }
        zoomOutButton.setOnAction { zoom(0.8) }
    }

    private fun ScrollPane.centerLabel(label: Label) {
        this.hvalue = (label.boundsInParent.width / 2 + label.boundsInParent.minX) / mapImageView.layoutBounds.width
        this.vvalue = (label.boundsInParent.height / 2 +label.boundsInParent.minY) / mapImageView.layoutBounds.height
    }

    private fun zoom(zoomFactor: Double, mousePoint: Point2D = mapScrollPane.viewportBounds.getCenterPoint()) {
        val valX = mapScrollPane.hvalue * (mapImageView.layoutBounds.width - mapScrollPane.viewportBounds.width)
        val valY = mapScrollPane.vvalue * (mapImageView.layoutBounds.height - mapScrollPane.viewportBounds.height)
        // calculate adjustment of scroll position.
        // Set zoom scale bound from 1x to 10x.
        val newHeight = Math.min(Math.max(mapImageView.prefHeight(-1.0) * zoomFactor, mapOriginalHeight.value), mapOriginalHeight.value * 10)
        val newZoomFactor = newHeight / mapImageView.prefHeight(-1.0)
        val adjustment = mousePoint.multiply(newZoomFactor - 1)
        mapImageView.fitHeight = newHeight
        mapScrollPane.layout()
        // Adjust scroll.
        mapScrollPane.hvalue = (valX + adjustment.x) / (mapImageView.layoutBounds.width - mapScrollPane.viewportBounds.width)
        mapScrollPane.vvalue = (valY + adjustment.y) / (mapImageView.layoutBounds.height - mapScrollPane.viewportBounds.height)
    }

    private fun Bounds.getCenterPoint(): Point2D {
        val x = this.width / 2 - this.minX
        val y = this.height / 2 - this.minY
        return Point2D(x, y)
    }

    private fun List<StateAndRef<ContractState>>.getParties() = map { it.state.data.participants.map { getModel<NetworkIdentityModel>().lookup(it) } }.flatten()
}