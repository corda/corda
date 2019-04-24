package net.corda.tools.testing.pinger

import net.corda.core.contracts.Amount
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.ThreadBox
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import rx.Subscription
import java.time.Clock
import java.time.Duration
import java.time.Instant

class FlowRunner(val rpcClient: CordaRPCOps, val target: Party, val notary: Party, val parallelFlows: Int, val waitDoneTimeSeconds: Long) {
    companion object {
        val log = contextLogger()
    }

    private data class StartTracker(val startTime: Instant, val flowHandle: FlowHandle<AbstractCashFlow.Result>)

    private val notify = Object()

    private class State {
        var running: Boolean = false
        var runner: Thread? = null
        val liveFlows: MutableMap<StateMachineRunId, StartTracker> = mutableMapOf()
        var stateMachineObserver: Subscription? = null
        var vaultObserver: Subscription? = null
    }

    private var state = ThreadBox(State())

    fun start() {
        log.info("Start")
        state.locked {
            if (running) {
                return
            }
            //TODO handle RPC disconnect
            //TODO audit state machine counts
            stateMachineObserver = rpcClient.stateMachinesFeed().updates.subscribe({
                when (it) {
                    is StateMachineUpdate.Added -> {
                        log.info("State Machine Added $it")
                        it.stateMachineInfo.progressTrackerStepAndUpdates?.updates?.subscribe()?.unsubscribe()
                    }
                    is StateMachineUpdate.Removed -> {
                        log.info("State Machine Removed ${it.id} ${it.result.getOrThrow()}")
                    }
                }
            }, { log.error("State machine observer error", it) })
            //TODO audit Vault events and query results
            vaultObserver = rpcClient.vaultTrackByWithPagingSpec(Cash.State::class.java, QueryCriteria.VaultQueryCriteria(), PageSpecification(1, 1))
                    .updates.subscribe({
                log.info("vault update produced ${it.produced.map { it.ref }} consumed ${it.consumed.map { it.ref }}")
            },
                    { log.error("Vault observer error", it) })
            runner = Thread { run() }.apply {
                name = "flow runner $target"
                isDaemon = false
            }
            running = true
            runner!!.start()
        }
    }

    private fun run() {
        while (true) {
            val newFlow = state.locked {
                if (running) {
                    if (liveFlows.size < parallelFlows) {
                        val amount = Amount.parseCurrency("10 USD")
                        val issuerRef = OpaqueBytes.of(0x01)
                        val flowHandle = rpcClient.startFlowDynamic(CashIssueAndPaymentFlow::class.java, amount, issuerRef, target, false, notary)
                        log.info("Started Flow ${flowHandle.id}")
                        liveFlows[flowHandle.id] = StartTracker(Clock.systemUTC().instant(), flowHandle)
                        log.info("Live flows count ${liveFlows.size}")
                        flowHandle
                    } else {
                        null
                    }
                } else {
                    if (liveFlows.isEmpty()) {
                        return
                    }
                    null
                }
            }
            if (newFlow != null) {
                newFlow.returnValue.then {
                    val info = state.locked {
                        val inf = liveFlows.remove(newFlow.id)
                        log.info("Live flows count ${liveFlows.size}")
                        inf
                    }
                    if (!it.isCancelled) {
                        val result = it.get()
                        if (info == null) {
                            log.warn("Unknown flowid ${newFlow.id} result $result")
                        } else {
                            val diff = Duration.between(info.startTime, Clock.systemUTC().instant()).toMillis()
                            log.info("Flow Complete ${newFlow.id} in $diff ms $result")
                        }
                    }
                    if (!it.isDone) {
                        newFlow.close()
                    }
                    synchronized(notify) {
                        notify.notifyAll()
                    }
                }
            }
            while (state.locked { running && (liveFlows.size >= parallelFlows) }) {
                synchronized(notify) {
                    notify.wait()
                }
            }
        }
    }

    fun stop(): Boolean {
        log.info("Stop")
        var valid = true
        val thread = state.locked {
            if (!running) {
                return valid
            }
            synchronized(notify) {
                running = false
                log.info("Waiting for ${liveFlows.size} flows to complete")
                notify.notifyAll()
            }
            val thread = runner
            runner = null
            thread

        }
        thread?.join(waitDoneTimeSeconds * 1000L)
        state.locked {
            stateMachineObserver?.unsubscribe()
            stateMachineObserver = null
            vaultObserver?.unsubscribe()
            vaultObserver = null
            for (flow in liveFlows.values) {
                log.error("Flow handle not signalled for ${flow.flowHandle.id}")
                valid = false
            }
        }
        return valid
    }
}