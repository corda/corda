/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
import net.corda.client.jfx.model.NodeMonitorModel
import net.corda.client.jfx.model.observableValue
import net.corda.core.utilities.contextLogger
import net.corda.explorer.model.CordaViewModel
import net.corda.explorer.model.SettingsModel
import net.corda.explorer.views.*
import net.corda.explorer.views.cordapps.cash.CashViewer
import net.corda.explorer.views.cordapps.iou.IOUViewer
import org.apache.commons.lang.SystemUtils
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.App
import tornadofx.addStageIcon
import tornadofx.find
import kotlin.system.exitProcess

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
        var nodeModel: NodeMonitorModel? = null

        // Login to Corda node
        super.start(stage)
        stage.minHeight = 600.0
        stage.minWidth = 800.0
        stage.isFullScreen = fullscreen.value
        stage.setOnCloseRequest {
            val button = Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to exit Corda explorer?").apply {
                initOwner(stage.scene.window)
            }.showAndWait().get()
            if (button == ButtonType.OK) {
                nodeModel?.close()
            } else {
                it.consume()
            }
        }

        val hostname = parameters.named["host"]
        val port = asInteger(parameters.named["port"])
        val username = parameters.named["username"]
        val password = parameters.named["password"]

        if ((hostname != null) && (port != null) && (username != null) && (password != null)) {
            try {
                nodeModel = loginView.login(hostname, port, username, password)
            } catch (e: Exception) {
                ExceptionDialog(e).apply { initOwner(stage.scene.window) }.showAndWait()
            }
        }

        if (nodeModel == null) {
            stage.hide()
            nodeModel = loginView.login()
        }
        addOptionalViews()
        (find(primaryView) as MainView).initializeControls()
        stage.show()
    }

    private fun addOptionalViews() {
        val iouView = find<IOUViewer>()
        Models.get<CordaViewModel>(Main::class).apply {
            if (iouView.isEnabledForNode()) {
                registerView(iouView)
            }
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
                exitProcess(1)
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
            registerView<StateMachineViewer>()
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
    ExplorerSimulation(options).startDemoNodes()
}
