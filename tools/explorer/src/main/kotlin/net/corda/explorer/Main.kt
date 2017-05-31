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
import net.corda.client.mock.Generator
import net.corda.client.mock.pickOne
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.GBP
import net.corda.core.contracts.USD
import net.corda.core.crypto.X509Utilities
import net.corda.core.failure
import net.corda.core.messaging.FlowHandle
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.success
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.loggerFor
import net.corda.explorer.model.CordaViewModel
import net.corda.explorer.model.SettingsModel
import net.corda.explorer.views.*
import net.corda.explorer.views.cordapps.cash.CashViewer
import net.corda.flows.CashExitFlow
import net.corda.flows.CashFlowCommand
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.node.driver.PortAllocation
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import org.apache.commons.lang.SystemUtils
import org.bouncycastle.asn1.x500.X500Name
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*
import java.time.Instant
import java.util.*

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
 * This main method will starts 5 nodes (Notary, Alice, Bob, UK Bank and USA Bank) locally for UI testing,
 * they will be on localhost ports 20003, 20006, 20009, 20012 and 20015 respectively.
 *
 * The simulation start with pre-allocating chunks of cash to each of the party in 2 currencies (USD, GBP), then it enter a loop to generate random events.
 * On each iteration, the issuers will execute a Cash Issue or Cash Exit flow (at a 9:1 ratio) and a random party will execute a move of cash to another random party.
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
        val notary = startNode(DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
        val alice = startNode(ALICE.name, rpcUsers = arrayListOf(user),
                advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))))
        val bob = startNode(BOB.name, rpcUsers = arrayListOf(user),
                advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))))
        val issuerGBP = startNode(X500Name("CN=UK Bank Plc,O=UK Bank Plc,L=London,C=UK"), rpcUsers = arrayListOf(manager),
                advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.GBP"))))
        val issuerUSD = startNode(X500Name("CN=USA Bank Corp,O=USA Bank Corp,L=New York,C=US"), rpcUsers = arrayListOf(manager),
                advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.USD"))))

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
            val aliceConnection = aliceClient.start(user.username, user.password)
            val aliceRPC = aliceConnection.proxy

            val bobClient = bobNode.rpcClientToNode()
            val bobConnection = bobClient.start(user.username, user.password)
            val bobRPC = bobConnection.proxy

            val issuerClientGBP = issuerNodeGBP.rpcClientToNode()
            val issuerGBPConnection = issuerClientGBP.start(manager.username, manager.password)
            val issuerRPCGBP = issuerGBPConnection.proxy

            val issuerClientUSD = issuerNodeUSD.rpcClientToNode()
            val issuerUSDConnection = issuerClientUSD.start(manager.username, manager.password)
            val issuerRPCUSD = issuerUSDConnection.proxy

            val issuers = mapOf(USD to issuerRPCUSD, GBP to issuerRPCGBP)

            val parties = listOf(aliceNode.nodeInfo.legalIdentity to aliceRPC,
                    bobNode.nodeInfo.legalIdentity to bobRPC,
                    issuerNodeGBP.nodeInfo.legalIdentity to issuerRPCGBP,
                    issuerNodeUSD.nodeInfo.legalIdentity to issuerRPCUSD)

            val eventGenerator = EventGenerator(
                    parties = parties.map { it.first },
                    notary = notaryNode.nodeInfo.notaryIdentity,
                    currencies = listOf(GBP, USD)
            )

            val maxIterations = 100_000
            // Log to logger when flow finish.
            fun FlowHandle<SignedTransaction>.log(seq: Int, name: String) {
                val out = "[$seq] $name $id :"
                returnValue.success {
                    Main.log.info("$out ${it.id} ${(it.tx.outputs.first().data as Cash.State).amount}")
                }.failure {
                    Main.log.info("$out ${it.message}")
                }
            }

            // Pre allocate some money to each party.
            eventGenerator.parties.forEach {
                for (ref in 0..1) {
                    for ((currency, issuer) in issuers) {
                        CashFlowCommand.IssueCash(Amount(1_000_000, currency), OpaqueBytes(ByteArray(1, { ref.toByte() })), it, notaryNode.nodeInfo.notaryIdentity).startFlow(issuer)
                    }
                }
            }

            for (i in 0..maxIterations) {
                Thread.sleep(300)
                // Issuer requests.
                eventGenerator.issuerGenerator.map { command ->
                    when (command) {
                        is CashFlowCommand.IssueCash -> issuers[command.amount.token]?.let {
                            println("${Instant.now()} [$i] ISSUING ${command.amount} with ref ${command.issueRef} to ${command.recipient}")
                            command.startFlow(it).log(i, "${command.amount.token}Issuer")
                        }
                        is CashFlowCommand.ExitCash -> issuers[command.amount.token]?.let {
                            println("${Instant.now()} [$i] EXITING ${command.amount} with ref ${command.issueRef}")
                            command.startFlow(it).log(i, "${command.amount.token}Exit")
                        }
                        else -> throw IllegalArgumentException("Unsupported command: $command")
                    }
                }.generate(SplittableRandom())

                // Party pay requests.
                eventGenerator.moveCashGenerator.combine(Generator.pickOne(parties)) { command, (party, rpc) ->
                    println("${Instant.now()} [$i] SENDING ${command.amount} from $party to ${command.recipient}")
                    command.startFlow(rpc).log(i, party.name.toString())
                }.generate(SplittableRandom())

            }
            println("Simulation completed")

            aliceConnection.close()
            bobConnection.close()
            issuerGBPConnection.close()
            issuerUSDConnection.close()
        }
        waitForAllNodesToFinish()
    }
}
