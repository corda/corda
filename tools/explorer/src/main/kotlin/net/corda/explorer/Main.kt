package net.corda.explorer

import com.apple.eawt.Application
import de.jensd.fx.glyphs.fontawesome.utils.FontAwesomeIconFactory
import javafx.embed.swing.SwingFXUtils
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.image.Image
import javafx.stage.Stage
import jfxtras.resources.JFXtrasFontRoboto
import net.corda.client.mock.EventGenerator
import net.corda.client.model.Models
import net.corda.client.model.observableValue
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.explorer.model.CordaViewModel
import net.corda.explorer.model.SettingsModel
import net.corda.explorer.views.*
import net.corda.explorer.views.cordapps.cash.CashViewer
import net.corda.flows.CashFlow
import net.corda.node.driver.PortAllocation
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import org.apache.commons.lang.SystemUtils
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.App
import tornadofx.addStageIcon
import tornadofx.find
import java.util.*

/**
 * Main class for Explorer, you will need Tornado FX to run the explorer.
 */
class Main : App(MainView::class) {
    private val loginView by inject<LoginView>()
    private val fullscreen by observableValue(SettingsModel::fullscreenProperty)

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
        stage.hide()
        loginView.login()
        stage.show()
    }

    init {
        // Shows any uncaught exception in exception dialog.
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
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
 *  This main method will starts 3 nodes (Notary, Alice and Bob) locally for UI testing, they will be on localhost:20002, 20004, 20006 respectively.
 */
fun main(args: Array<String>) {
    val portAllocation = PortAllocation.Incremental(20000)
    driver(portAllocation = portAllocation) {
        val user = User("user1", "test", permissions = setOf(startFlowPermission<CashFlow>()))
        // TODO : Supported flow should be exposed somehow from the node instead of set of ServiceInfo.
        val notary = startNode("Notary", advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)), customOverrides = mapOf("nearestCity" to "Zurich"))
        val alice = startNode("Alice", rpcUsers = arrayListOf(user), advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))), customOverrides = mapOf("nearestCity" to "Paris"))
        val bob = startNode("Bob", rpcUsers = arrayListOf(user), advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))), customOverrides = mapOf("nearestCity" to "Frankfurt"))
        val issuer = startNode("Royal Mint", rpcUsers = arrayListOf(user), advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))), customOverrides = mapOf("nearestCity" to "London"))

        val notaryNode = notary.get()
        val aliceNode = alice.get()
        val bobNode = bob.get()
        val issuerNode = issuer.get()

        arrayOf(notaryNode, aliceNode, bobNode, issuerNode).forEach {
            println("${it.nodeInfo.legalIdentity} started on ${ArtemisMessagingComponent.toHostAndPort(it.nodeInfo.address)}")
        }
        // Register with alice to use alice's RPC proxy to create random events.
        val aliceClient = CordaRPCClient(ArtemisMessagingComponent.toHostAndPort(aliceNode.nodeInfo.address), FullNodeConfiguration(aliceNode.config))
        aliceClient.start(user.username, user.password)
        val aliceRPC = aliceClient.proxy()

        val bobClient = CordaRPCClient(ArtemisMessagingComponent.toHostAndPort(bobNode.nodeInfo.address), FullNodeConfiguration(bobNode.config))
        bobClient.start(user.username, user.password)
        val bobRPC = bobClient.proxy()

        val issuerClient = CordaRPCClient(ArtemisMessagingComponent.toHostAndPort(issuerNode.nodeInfo.address), FullNodeConfiguration(issuerNode.config))
        issuerClient.start(user.username, user.password)
        val bocRPC = issuerClient.proxy()

        val eventGenerator = EventGenerator(
                parties = listOf(aliceNode.nodeInfo.legalIdentity, bobNode.nodeInfo.legalIdentity, issuerNode.nodeInfo.legalIdentity),
                notary = notaryNode.nodeInfo.notaryIdentity
        )

        for (i in 0..1000) {
            Thread.sleep(500)
            listOf(aliceRPC, bobRPC).forEach {
                eventGenerator.clientCommandGenerator.map { command ->
                    it.startFlow(::CashFlow, command)
                    Unit
                }.generate(SplittableRandom())
            }
            eventGenerator.bankOfCordaCommandGenerator.map { command ->
                bocRPC.startFlow(::CashFlow, command)
                Unit
            }.generate(SplittableRandom())
        }

        aliceClient.close()
        bobClient.close()
        issuerClient.close()
        waitForAllNodesToFinish()
    }
}