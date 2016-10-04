package com.r3corda.explorer

import com.r3corda.client.mock.EventGenerator
import com.r3corda.client.model.Models
import com.r3corda.client.model.NodeMonitorModel
import com.r3corda.client.model.subject
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.explorer.model.IdentityModel
import com.r3corda.node.driver.PortAllocation
import com.r3corda.node.driver.driver
import com.r3corda.node.driver.startClient
import com.r3corda.node.services.transactions.SimpleNotaryService
import javafx.stage.Stage
import rx.subjects.Subject
import tornadofx.App
import java.util.*

class Main : App() {
    override val primaryView = MainWindow::class
    val aliceOutStream: Subject<ClientToServiceCommand, ClientToServiceCommand> by subject(NodeMonitorModel::clientToService)

    override fun start(stage: Stage) {

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            System.exit(1)
        }

        super.start(stage)

        // start the driver on another thread
        // TODO Change this to connecting to an actual node (specified on cli/in a config) once we're happy with the code
        Thread({

            val portAllocation = PortAllocation.Incremental(20000)
            driver(portAllocation = portAllocation) {

                val aliceNodeFuture = startNode("Alice")
                val notaryNodeFuture = startNode("Notary", advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))

                val aliceNode = aliceNodeFuture.get()
                val notaryNode = notaryNodeFuture.get()

                val aliceClient = startClient(aliceNode).get()

                Models.get<IdentityModel>(Main::class).myIdentity.set(aliceNode.legalIdentity)
                Models.get<NodeMonitorModel>(Main::class).register(aliceNode, aliceClient.config.certificatesPath)

                for (i in 0 .. 10000) {
                    Thread.sleep(500)

                    val eventGenerator = EventGenerator(
                            parties = listOf(aliceNode.legalIdentity),
                            notary = notaryNode.notaryIdentity
                    )

                    eventGenerator.clientToServiceCommandGenerator.map { command ->
                        aliceOutStream.onNext(command)
                    }.generate(Random())
                }

                waitForAllNodesToFinish()
            }

        }).start()
    }
}
