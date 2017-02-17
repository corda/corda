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
import net.corda.client.mock.EventGenerator
import net.corda.client.model.Models
import net.corda.client.model.observableValue
import net.corda.core.contracts.GBP
import net.corda.core.contracts.USD
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.explorer.model.CordaViewModel
import net.corda.explorer.model.SettingsModel
import net.corda.explorer.views.*
import net.corda.explorer.views.cordapps.cash.CashViewer
import net.corda.flows.CashExitFlow
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.node.driver.PortAllocation
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.messaging.ArtemisMessagingComponent
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
 *  This main method will starts 5 nodes (Notary, Alice, Bob, UK Bank and USA Bank) locally for UI testing, they will be on localhost:20002, 20004, 20006, 20008, 20010 respectively.
 */
fun main(args: Array<String>) {
    val portAllocation = PortAllocation.Incremental(20000)
    driver(portAllocation = portAllocation) {
        val user = User("user1", "test", permissions = setOf(
                startFlowPermission<CashPaymentFlow>()
        ))
        val manager = User("manager", "test", permissions = setOf(
                startFlowPermission<CashIssueFlow>(),
                startFlowPermission<CashPaymentFlow>(),
                startFlowPermission<CashExitFlow>(),
                startFlowPermission<IssuanceRequester>())
        )
        // TODO : Supported flow should be exposed somehow from the node instead of set of ServiceInfo.
        val notary = startNode("Notary", advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)),
                                    customOverrides = mapOf("nearestCity" to "Zurich"))
        val alice = startNode("Alice", rpcUsers = arrayListOf(user),
                                    advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))),
                                    customOverrides = mapOf("nearestCity" to "Milan"))
        val bob = startNode("Bob", rpcUsers = arrayListOf(user),
                                    advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))),
                                    customOverrides = mapOf("nearestCity" to "Madrid"))
        val issuerGBP = startNode("UK Bank Plc", rpcUsers = arrayListOf(manager),
                                    advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.GBP"))),
                                    customOverrides = mapOf("nearestCity" to "London"))
        val issuerUSD = startNode("USA Bank Corp", rpcUsers = arrayListOf(manager),
                                    advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.USD"))),
                                    customOverrides = mapOf("nearestCity" to "New York"))

        val notaryNode = notary.get()
        val aliceNode = alice.get()
        val bobNode = bob.get()
        val issuerNodeGBP = issuerGBP.get()
        val issuerNodeUSD = issuerUSD.get()

        arrayOf(notaryNode, aliceNode, bobNode, issuerNodeGBP, issuerNodeUSD).forEach {
            println("${it.nodeInfo.legalIdentity} started on ${ArtemisMessagingComponent.toHostAndPort(it.nodeInfo.address)}")
        }

        val parser = OptionParser("S")
        val options = parser.parse(*args)
        if (options.has("S")) {
            println("Running simulation mode ...")

            // Register with alice to use alice's RPC proxy to create random events.
            val aliceClient = aliceNode.rpcClientToNode()
            aliceClient.start(user.username, user.password)
            val aliceRPC = aliceClient.proxy()

            val bobClient = bobNode.rpcClientToNode()
            bobClient.start(user.username, user.password)
            val bobRPC = bobClient.proxy()

            val issuerClientGBP = issuerNodeGBP.rpcClientToNode()
            issuerClientGBP.start(manager.username, manager.password)
            val issuerRPCGBP = issuerClientGBP.proxy()

            val issuerClientUSD = issuerNodeGBP.rpcClientToNode()  // TODO This should be issuerNodeUSD
            issuerClientUSD.start(manager.username, manager.password)
            val issuerRPCUSD = issuerClientUSD.proxy()

            val eventGenerator = EventGenerator(
                    parties = listOf(aliceNode.nodeInfo.legalIdentity, bobNode.nodeInfo.legalIdentity),
                    notary = notaryNode.nodeInfo.notaryIdentity,
                    issuers = listOf(issuerNodeGBP.nodeInfo.legalIdentity, issuerNodeUSD.nodeInfo.legalIdentity)
            )
            val issuerGBPEventGenerator = EventGenerator(
                    parties = listOf(issuerNodeGBP.nodeInfo.legalIdentity, aliceNode.nodeInfo.legalIdentity, bobNode.nodeInfo.legalIdentity),
                    notary = notaryNode.nodeInfo.notaryIdentity,
                    currencies = listOf(GBP)
            )
            val issuerUSDEventGenerator = EventGenerator(
                    parties = listOf(issuerNodeUSD.nodeInfo.legalIdentity, aliceNode.nodeInfo.legalIdentity, bobNode.nodeInfo.legalIdentity),
                    notary = notaryNode.nodeInfo.notaryIdentity,
                    currencies = listOf(USD)
            )

            for (i in 0..1000) {
                Thread.sleep(500)
                // Party pay requests
                listOf(aliceRPC, bobRPC).forEach {
                    eventGenerator.clientCommandGenerator.map { command ->
                        command.startFlow(it)
                        Unit
                    }.generate(SplittableRandom())
                }
                // Exit requests
                issuerGBPEventGenerator.bankOfCordaExitGenerator.map { command ->
                    command.startFlow(issuerRPCGBP)
                    Unit
                }.generate(SplittableRandom())
                issuerUSDEventGenerator.bankOfCordaExitGenerator.map { command ->
                    command.startFlow(issuerRPCUSD)
                    Unit
                }.generate(SplittableRandom())
                // Issuer requests
                issuerGBPEventGenerator.bankOfCordaIssueGenerator.map { command ->
                    command.startFlow(issuerRPCGBP)
                    Unit
                }.generate(SplittableRandom())
                issuerUSDEventGenerator.bankOfCordaIssueGenerator.map { command ->
                    command.startFlow(issuerRPCUSD)
                    Unit
                }.generate(SplittableRandom())
            }
            aliceClient.close()
            bobClient.close()
            issuerClientGBP.close()
            issuerClientUSD.close()
        }
        waitForAllNodesToFinish()
    }
}
