package net.corda.explorer

import net.corda.client.mock.EventGenerator
import net.corda.client.model.Models
import net.corda.client.model.NodeMonitorModel
import net.corda.core.node.services.ServiceInfo
import net.corda.explorer.views.runInFxApplicationThread
import net.corda.node.driver.PortAllocation
import net.corda.node.driver.driver
import net.corda.node.internal.CordaRPCOpsImpl
import net.corda.node.services.User
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.transactions.SimpleNotaryService
import javafx.stage.Stage
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.App
import java.util.*

/**
 * Main class for Explorer, you will need Tornado FX to run the explorer.
 */
class Main : App() {
    override val primaryView = MainWindow::class

    override fun start(stage: Stage) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            // Show exceptions in exception dialog.
            runInFxApplicationThread {
                // [showAndWait] need to be in the FX thread
                ExceptionDialog(throwable).showAndWait()
                System.exit(1)
            }
        }
        super.start(stage)
    }
}

/**
 *  This main method will starts 3 nodes (Notary, Alice and Bob) locally for UI testing, they will be on localhost:20002, 20004, 20006 respectively.
 */
fun main(args: Array<String>) {
    val portAllocation = PortAllocation.Incremental(20000)
    driver(portAllocation = portAllocation) {
        val user = User("user1", "test", permissions = setOf(CordaRPCOpsImpl.CASH_PERMISSION))
        val notary = startNode("Notary", advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
        val alice = startNode("Alice", rpcUsers = arrayListOf(user))
        val bob = startNode("Bob", rpcUsers = arrayListOf(user))

        val notaryNode = notary.get()
        val aliceNode = alice.get()
        val bobNode = bob.get()

        arrayOf(notaryNode, aliceNode, bobNode).forEach {
            println("${it.nodeInfo.legalIdentity} started on ${ArtemisMessagingComponent.toHostAndPort(it.nodeInfo.address)}")
        }

        // Register with alice to use alice's RPC proxy to create random events.
        Models.get<NodeMonitorModel>(Main::class).register(ArtemisMessagingComponent.toHostAndPort(aliceNode.nodeInfo.address), FullNodeConfiguration(aliceNode.config), user.username, user.password)
        val rpcProxy = Models.get<NodeMonitorModel>(Main::class).proxyObservable.get()

        for (i in 0..10000) {
            Thread.sleep(500)
            val eventGenerator = EventGenerator(
                    parties = listOf(aliceNode.nodeInfo.legalIdentity, bobNode.nodeInfo.legalIdentity),
                    notary = notaryNode.nodeInfo.notaryIdentity
            )
            eventGenerator.clientToServiceCommandGenerator.map { command ->
                rpcProxy?.executeCommand(command)
            }.generate(Random())
        }
        waitForAllNodesToFinish()
    }
}
