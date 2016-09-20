package com.r3corda.explorer

import com.r3corda.explorer.views.TopLevel
import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconFactory
import jfxtras.resources.JFXtrasFontRoboto
import tornadofx.*

/**
 * The root view embeds the [Shell] and provides support for the status bar, and modal dialogs.
 */
class MainWindow : View() {
    private val toplevel: TopLevel by inject()
    override val root = toplevel.root

    init {
        // Do this first before creating the notification bar, so it can autosize itself properly.
        loadFontsAndStyles()
    }

    private fun loadFontsAndStyles() {
        JFXtrasFontRoboto.loadAll()
        importStylesheet("/com/r3corda/explorer/css/wallet.css")
        FontAwesomeIconFactory.get()   // Force initialisation.
        root.styleClass += "root"
    }
}
