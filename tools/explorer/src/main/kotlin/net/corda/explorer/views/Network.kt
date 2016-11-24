package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import net.corda.client.fxutils.map
import net.corda.client.model.NetworkIdentityModel
import net.corda.client.model.observableList
import net.corda.client.model.observableValue
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

    // Components
    private val myIdentityPane by fxid<BorderPane>()
    private val notaryList by fxid<VBox>()
    private val peerList by fxid<VBox>()
    private val mapScrollPane by fxid<ScrollPane>()
    private val mapPane by fxid<Pane>()

    // Create a strong ref to prevent GC.
    private val notaryButtons = notaries.map { it.render() }
    private val peerButtons = peers.filtered { it != myIdentity.value }.map { it.render() }
    private val coordinate = Bindings.createObjectBinding({
        myIdentity.value?.physicalLocation?.coordinate?.project(mapPane.width, mapPane.height, 85.0511, -85.0511, -180.0, 180.0)?.let {
            Pair(it.first - 15, it.second - 10)
        }
    }, arrayOf(mapPane.widthProperty(), mapPane.heightProperty(), myIdentity))

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

        val myLocation = Label("", FontAwesomeIconView(FontAwesomeIcon.DOT_CIRCLE_ALT)).apply { contentDisplay = ContentDisplay.TOP }
        myLocation.textProperty().bind(myIdentity.map { it?.legalIdentity?.name })

        myLocation.layoutXProperty().bind(coordinate.map { it?.first })
        myLocation.layoutYProperty().bind(coordinate.map { it?.second })
        mapPane.add(myLocation)

        val scroll = Bindings.createObjectBinding({
            val width = mapScrollPane.content.boundsInLocal.width
            val height = mapScrollPane.content.boundsInLocal.height
            val x = myLocation.boundsInParent.maxX
            val y = myLocation.boundsInParent.minY
            Pair(x / width, y / height)
        }, arrayOf(coordinate))

        mapScrollPane.vvalueProperty().bind(scroll.map { it.second })
        mapScrollPane.hvalueProperty().bind(scroll.map { it.first })
    }
}