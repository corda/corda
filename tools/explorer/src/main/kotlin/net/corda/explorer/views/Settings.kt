package net.corda.explorer.views

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.scene.Node
import net.corda.explorer.model.CordaView

// TODO : Allow user to configure preferences, e.g Reporting currency, full screen mode etc.
class Settings : CordaView() {
    override val root = underConstruction()
    override val widget: Node? = null
    override val icon = FontAwesomeIcon.COGS
}