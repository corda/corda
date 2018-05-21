package net.corda.explorer

import com.apple.eawt.Application
import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconFactory
import javafx.embed.swing.SwingFXUtils
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.image.Image
import javafx.stage.Stage
import jfxtras.resources.JFXtrasFontRoboto
import joptsimple.OptionParser
import net.corda.client.jfx.model.Models
import net.corda.client.jfx.model.observableValue
import net.corda.core.utilities.contextLogger
import net.corda.explorer.model.CordaViewModel
import net.corda.explorer.model.SettingsModel
import net.corda.explorer.views.*
import net.corda.explorer.views.cordapps.cash.CashViewer
import org.apache.commons.lang.SystemUtils
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.App
import tornadofx.addStageIcon
import tornadofx.find

/**
 * Main class for Explorer, you will need Tornado FX to run the explorer.
 */
class Main : App(MainView::class) {
    private val loginView by inject<LoginView>()
    private val fullscreen by observableValue(SettingsModel::fullscreenProperty)

    companion object {
        internal val log = contextLogger()
    }

    override fun start(stage: Stage) {
        // Login to Corda node
        super.start(stage)
        stage.minHeight = 600.0
        stage.minWidth = 800.0
        stage.isFullScreen = fullscreen.value
        stage.setOnCloseRequest {
            val button = Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to exit Corda explorer?").apply {
                initOwner(stage.scene.window)
            }.showAndWait().get()
            if (button != ButtonType.OK) it.consume()
        }

        val hostname = parameters.named["host"]
        val port = asInteger(parameters.named["port"])
        val username = parameters.named["username"]
        val password = parameters.named["password"]
        var isLoggedIn = false

        if ((hostname != null) && (port != null) && (username != null) && (password != null)) {
            try {
                loginView.login(hostname, port, username, password)
                isLoggedIn = true
            } catch (e: Exception) {
                ExceptionDialog(e).apply { initOwner(stage.scene.window) }.showAndWait()
            }
        }

        if (!isLoggedIn) {
            stage.hide()
            loginView.login()
            stage.show()
        }
    }

    private fun asInteger(s: String?): Int? {
        return try {
            s?.toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    init {
        // Shows any uncaught exception in exception dialog.
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            throwable.printStackTrace()
            // Show exceptions in exception dialog. Ensure this runs in application thread.
            runInFxApplicationThread {
                // [showAndWait] need to be in the FX thread.
                ExceptionDialog(throwable).showAndWait()
                System.exit(1)
            }
        }
        // Do this first before creating the notification bar, so it can autosize itself properly.
        loadFontsAndStyles()
        // Add Corda logo to OSX dock and windows icon.
        val cordaLogo = Image(javaClass.getResourceAsStream("images/Logo-03.png"))
        if (SystemUtils.IS_OS_MAC_OSX) {
            Application.getApplication().dockIconImage = SwingFXUtils.fromFXImage(cordaLogo, null)
        }
        addStageIcon(cordaLogo)
        // Register views.
        Models.get<CordaViewModel>(Main::class).apply {
            // TODO : This could block the UI thread when number of views increase, maybe we can make this async and display a loading screen.
            // Stock Views.
            registerView<Dashboard>()
            registerView<TransactionViewer>()
            // CordApps Views.
            registerView<CashViewer>()
            // Tools.
            registerView<Network>()
            registerView<Settings>()
            // Default view to Dashboard.
            selectedView.set(find<Dashboard>())
        }
    }

    private fun loadFontsAndStyles() {
        JFXtrasFontRoboto.loadAll()
        FontAwesomeIconFactory.get()   // Force initialisation.
    }
}

/**
 * This main method will starts 5 nodes (Notary, USA Bank, UK Bank, Bob and Alice) locally for UI testing,
 * they will be on localhost ports 20005, 20008, 20011, 20014 and 20017 respectively.
 *
 * The simulation start with pre-allocating chunks of cash to each of the party in 2 currencies (USD, GBP), then it enter a loop to generate random events.
 * On each iteration, the issuers will execute a Cash Issue or Cash Exit flow (at a 9:1 ratio) and a random party will execute a move of cash to another random party.
 */
fun main(args: Array<String>) {
    val parser = OptionParser("SF")
    val options = parser.parse(*args)
    ExplorerSimulation(options)
}
