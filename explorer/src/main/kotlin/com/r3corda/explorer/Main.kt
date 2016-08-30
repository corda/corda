package com.r3corda.explorer

import com.r3corda.client.WalletMonitorClient
import com.r3corda.client.mock.*
import com.r3corda.client.model.*
import com.r3corda.core.contracts.*
import com.r3corda.node.driver.PortAllocation
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.driver.driver
import com.r3corda.node.driver.startClient
import com.r3corda.node.services.transactions.SimpleNotaryService
import javafx.stage.Stage
import org.reactfx.EventSource
import tornadofx.App
import java.util.*

class Main : App() {
    override val primaryView = MainWindow::class
    val aliceOutStream: org.reactfx.EventSink<ClientToServiceCommand> by sink(WalletMonitorModel::clientToService)

    override fun start(stage: Stage) {

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            System.exit(1)
        }

        super.start(stage)

        // start the driver on another thread
        Thread({

            val portAllocation = PortAllocation.Incremental(20000)
            driver(portAllocation = portAllocation) {

                val aliceNodeFuture = startNode("Alice")
                val bobNodeFuture = startNode("Bob")
                val notaryNodeFuture = startNode("Notary", advertisedServices = setOf(SimpleNotaryService.Type))

                val aliceNode = aliceNodeFuture.get()
                val bobNode = bobNodeFuture.get()
                val notaryNode = notaryNodeFuture.get()

                val aliceClient = startClient(aliceNode).get()

                Models.get<WalletMonitorModel>(Main::class).register(aliceClient, aliceNode)

                val bobInStream = EventSource<ServiceToClientEvent>()
                val bobOutStream = EventSource<ClientToServiceCommand>()

                val bobClient = startClient(bobNode).get()
                val bobMonitorClient = WalletMonitorClient(bobClient, bobNode, bobOutStream, bobInStream)
                assert(bobMonitorClient.register().get())

                for (i in 0 .. 10000) {
                    Thread.sleep(500)

                    val eventGenerator = EventGenerator(
                            parties = listOf(aliceNode.identity, bobNode.identity),
                            notary = notaryNode.identity
                    )

                    eventGenerator.clientToServiceCommandGenerator.combine(Generator.oneOf(listOf(aliceOutStream, bobOutStream))) {
                        command, stream -> stream.push(command)
                    }.generate(Random())
                }

                waitForAllNodesToFinish()
            }

        }).start()
    }
}

