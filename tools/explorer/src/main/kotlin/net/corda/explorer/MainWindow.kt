package net.corda.explorer

import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconFactory
import jfxtras.resources.JFXtrasFontRoboto
import net.corda.client.model.Models
import net.corda.client.model.NodeMonitorModel
import net.corda.explorer.views.LoginView
import net.corda.explorer.views.TopLevel
import net.corda.node.services.config.configureTestSSL
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
        importStylesheet("/net/corda/explorer/css/wallet.css")
        FontAwesomeIconFactory.get()   // Force initialisation.
        root.styleClass += "root"
    }
}
