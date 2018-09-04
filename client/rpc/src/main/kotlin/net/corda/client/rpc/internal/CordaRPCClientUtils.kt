package net.corda.client.rpc.internal

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.StateMachineUpdate
import rx.Observable
import rx.subjects.PublishSubject

/** Utility which exposes the internal Corda RPC constructor to other internal Corda components */
fun createCordaRPCClientWithSslAndClassLoader(
        hostAndPort: NetworkHostAndPort,
        configuration: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT,
        sslConfiguration: ClientRpcSslOptions? = null,
        classLoader: ClassLoader? = null
) = CordaRPCClient.createWithSslAndClassLoader(hostAndPort, configuration, sslConfiguration, classLoader)

fun CordaRPCOps.drainAndShutdown(): Observable<Unit> {

    setFlowsDrainingModeEnabled(true)
    return pendingFlowsCount().updates
            .doOnError { error ->
                throw error
            }
            .doOnCompleted { shutdown() }.map { }
}

// TODO sollecitom merge with the one in CordaRPCOpsImpl
private fun CordaRPCOps.pendingFlowsCount(): DataFeed<Int, Pair<Int, Int>> {

    val stateMachineState = stateMachinesFeed()
    var pendingFlowsCount = stateMachineState.snapshot.size
    var completedFlowsCount = 0
    val updates = PublishSubject.create<Pair<Int, Int>>()
    stateMachineState
            .updates
            .doOnNext { update ->
                when (update) {
                    is StateMachineUpdate.Added -> {
                        pendingFlowsCount++
                        updates.onNext(completedFlowsCount to pendingFlowsCount)
                    }
                    is StateMachineUpdate.Removed -> {
                        completedFlowsCount++
                        updates.onNext(completedFlowsCount to pendingFlowsCount)
                        if (completedFlowsCount == pendingFlowsCount) {
                            updates.onCompleted()
                        }
                    }
                }
            }.subscribe()
    if (completedFlowsCount == 0) {
        updates.onCompleted()
    }
    return DataFeed(pendingFlowsCount, updates)
}