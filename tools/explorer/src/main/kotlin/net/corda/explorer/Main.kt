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
import net.corda.client.mock.EventGenerator
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.GBP
import net.corda.core.contracts.USD
import net.corda.core.messaging.FlowHandle
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.loggerFor
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
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import org.apache.commons.lang.SystemUtils
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.App
import tornadofx.addStageIcon
import tornadofx.find
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import kotlin.concurrent.thread

/**
 * Main class for Explorer, you will need Tornado FX to run the explorer.
 */
class Main : App(MainView::class) {
    private val loginView by inject<LoginView>()
    private val fullscreen by observableValue(SettingsModel::fullscreenProperty)

    companion object {
        val log = loggerFor<Main>()
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
        try {
            return s?.toInt()
        } catch (e: NumberFormatException) {
            return null
        }
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
 * This main method will starts 5 nodes (Notary, Alice, Bob, UK Bank and USA Bank) locally for UI testing,
 * they will be on localhost ports 20003, 20006, 20009, 20012 and 20015 respectively.
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
            println("${it.nodeInfo.legalIdentity} started on ${it.configuration.rpcAddress}")
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

            val issuerClientUSD = issuerNodeUSD.rpcClientToNode()
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

            val maxIterations = 100000
            val flowHandles = mapOf(
                    "GBPIssuer" to ArrayBlockingQueue<FlowHandle<SignedTransaction>>(maxIterations+1),
                    "USDIssuer" to ArrayBlockingQueue<FlowHandle<SignedTransaction>>(maxIterations+1),
                    "Alice" to ArrayBlockingQueue<FlowHandle<SignedTransaction>>(maxIterations+1),
                    "Bob" to ArrayBlockingQueue<FlowHandle<SignedTransaction>>(maxIterations+1),
                    "GBPExit" to ArrayBlockingQueue<FlowHandle<SignedTransaction>>(maxIterations+1),
                    "USDExit" to ArrayBlockingQueue<FlowHandle<SignedTransaction>>(maxIterations+1)
            )

            flowHandles.forEach {
                thread {
                    for (i in 0..maxIterations) {
                        val item = it.value.take()
                        val out = "[$i] ${it.key} ${item.id} :"
                        try {
                            val result = item.returnValue.get()
                            Main.log.info("$out ${result.id} ${(result.tx.outputs.first().data as Cash.State).amount}")
                        } catch(e: ExecutionException) {
                            Main.log.info("$out ${e.cause!!.message}")
                        }
                    }
                }
            }

            for (i in 0..maxIterations) {
                Thread.sleep(500)

                // Issuer requests
                if ((i % 5) == 0) {
                    issuerGBPEventGenerator.bankOfCordaIssueGenerator.map { command ->
                        println("[$i] ISSUING ${command.amount} with ref ${command.issueRef} to ${command.recipient}")
                        val cmd = command.startFlow(issuerRPCGBP)
                        flowHandles["GBPIssuer"]?.add(cmd)
                        cmd?.progress?.subscribe({},{})?.unsubscribe()
                        Unit
                    }.generate(SplittableRandom())
                    issuerUSDEventGenerator.bankOfCordaIssueGenerator.map { command ->
                        println("[$i] ISSUING ${command.amount} with ref ${command.issueRef} to ${command.recipient}")
                        val cmd = command.startFlow(issuerRPCUSD)
                        flowHandles["USDIssuer"]?.add(cmd)
                        cmd?.progress?.subscribe({},{})?.unsubscribe()
                        Unit
                    }.generate(SplittableRandom())
                }

                // Exit requests
                if ((i % 10) == 0) {
                    issuerGBPEventGenerator.bankOfCordaExitGenerator.map { command ->
                        println("[$i] EXITING ${command.amount} with ref ${command.issueRef}")
                        val cmd = command.startFlow(issuerRPCGBP)
                        flowHandles["GBPExit"]?.add(cmd)
                        cmd?.progress?.subscribe({},{})?.unsubscribe()
                        Unit
                    }.generate(SplittableRandom())
                    issuerUSDEventGenerator.bankOfCordaExitGenerator.map { command ->
                        println("[$i] EXITING ${command.amount} with ref ${command.issueRef}")
                        val cmd = command.startFlow(issuerRPCUSD)
                        flowHandles["USDExit"]?.add(cmd)
                        cmd?.progress?.subscribe({},{})?.unsubscribe()
                        Unit
                    }.generate(SplittableRandom())
                }

                // Party pay requests

                // Alice
                eventGenerator.clientCommandGenerator.map { command ->
                    println("[$i] SENDING ${command.amount} from ${aliceRPC.nodeIdentity().legalIdentity} to ${command.recipient}")
                    val cmd = command.startFlow(aliceRPC)
                    flowHandles["Alice"]?.add(cmd)
                    cmd?.progress?.subscribe({},{})?.unsubscribe()
                    Unit
                }.generate(SplittableRandom())

                // Bob
                eventGenerator.clientCommandGenerator.map { command ->
                    println("[$i] SENDING ${command.amount} from ${bobRPC.nodeIdentity().legalIdentity} to ${command.recipient}")
                    val cmd = command.startFlow(bobRPC)
                    flowHandles["Bob"]?.add(cmd)
                    cmd?.progress?.subscribe({},{})?.unsubscribe()
                    Unit
                }.generate(SplittableRandom())
            }

            println("Simulation completed")

            aliceClient.close()
            bobClient.close()
            issuerClientGBP.close()
            issuerClientUSD.close()
        }
        waitForAllNodesToFinish()
    }
}
