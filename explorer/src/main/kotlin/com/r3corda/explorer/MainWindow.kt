package com.r3corda.explorer

import com.r3corda.client.model.Models
import com.r3corda.client.model.NodeMonitorModel
import com.r3corda.explorer.views.LoginView
import com.r3corda.explorer.views.TopLevel
import com.r3corda.node.services.config.configureTestSSL
import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconFactory
import jfxtras.resources.JFXtrasFontRoboto
import tornadofx.View
import tornadofx.importStylesheet

/**
 * The root view embeds the [Shell] and provides support for the status bar, and modal dialogs.
 */
class MainWindow : View() {
    private val toplevel: TopLevel by inject()
    override val root = toplevel.root
    private val loginView by inject<LoginView>()

    init {
        // Do this first before creating the notification bar, so it can autosize itself properly.
        loadFontsAndStyles()
        loginView.login { hostAndPort, username, password ->
            Models.get<NodeMonitorModel>(MainWindow::class).register(hostAndPort, configureTestSSL(), username, password)
        }
    }

    private fun loadFontsAndStyles() {
        JFXtrasFontRoboto.loadAll()
        importStylesheet("/com/r3corda/explorer/css/wallet.css")
        FontAwesomeIconFactory.get()   // Force initialisation.
        root.styleClass += "root"
    }
}