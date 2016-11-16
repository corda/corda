package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.scene.Node
import net.corda.explorer.model.CordaView

// TODO : Construct a node map using node info and display hem on a world map.
// TODO : Allow user to see transactions between nodes on a world map.
class Network : CordaView() {
    override val root = underConstruction()
    override val widget: Node? = null
    override val icon = FontAwesomeIcon.GLOBE
}