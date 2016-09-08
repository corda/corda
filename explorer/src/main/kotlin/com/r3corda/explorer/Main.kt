package com.r3corda.explorer

import com.r3corda.client.WalletMonitorClient
import com.r3corda.client.mock.EventGenerator
import com.r3corda.client.mock.Generator
import com.r3corda.client.mock.oneOf
import com.r3corda.client.model.Models
import com.r3corda.client.model.WalletMonitorModel
import com.r3corda.client.model.observer
import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.node.driver.PortAllocation
import com.r3corda.node.driver.driver
import com.r3corda.node.driver.startClient
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.services.transactions.SimpleNotaryService
import javafx.stage.Stage
import rx.Observer
import rx.subjects.PublishSubject
import tornadofx.App
import java.util.*

class Main : App() {
    override val primaryView = MainWindow::class
    val aliceOutStream: Observer<ClientToServiceCommand> by observer(WalletMonitorModel::clientToService)

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

                val bobInStream = PublishSubject.create<ServiceToClientEvent>()
                val bobOutStream = PublishSubject.create<ClientToServiceCommand>()

                val bobClient = startClient(bobNode).get()
                val bobMonitorClient = WalletMonitorClient(bobClient, bobNode, bobOutStream, bobInStream, PublishSubject.create())
                assert(bobMonitorClient.register().get())

                for (i in 0 .. 10000) {
                    Thread.sleep(500)

                    val eventGenerator = EventGenerator(
                            parties = listOf(aliceNode.identity, bobNode.identity),
                            notary = notaryNode.identity
                    )

                    eventGenerator.clientToServiceCommandGenerator.combine(Generator.oneOf(listOf(aliceOutStream, bobOutStream))) {
                        command, stream -> stream.onNext(command)
                    }.generate(Random())
                }

                waitForAllNodesToFinish()
            }

        }).start()
    }
}

