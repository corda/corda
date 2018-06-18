package net.corda.testing.node.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.addShutdownHook
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class ShutdownManager(private val executorService: ExecutorService) {
    private class State {
        val registeredShutdowns = ArrayList<CordaFuture<() -> Unit>>()
        var isShuttingDown = false
        var isShutdown = false
    }

    private val state = ThreadBox(State())

    companion object {
        private val log = contextLogger()
        inline fun <A> run(providedExecutorService: ExecutorService? = null, block: ShutdownManager.() -> A): A {
            val executorService = providedExecutorService ?: Executors.newScheduledThreadPool(1)
            val shutdownManager = ShutdownManager(executorService)
            try {
                return block(shutdownManager)
            } finally {
                shutdownManager.shutdown()
                providedExecutorService ?: executorService.shutdown()
            }
        }
    }

    fun shutdown() {
        state.locked { isShuttingDown = true }
        val shutdownActionFutures = state.locked {
            if (isShutdown) {
                emptyList<CordaFuture<() -> Unit>>()
            } else {
                isShutdown = true
                registeredShutdowns
            }
        }

        val shutdowns = shutdownActionFutures.map { Try.on { it.getOrThrow(60.seconds) } }
        shutdowns.reversed().forEach {
            when (it) {
                is Try.Success ->
                    try {
                        it.value()
                    } catch (t: Throwable) {
                        log.warn("Exception while calling a shutdown action, this might create resource leaks", t)
                    }
                is Try.Failure -> log.warn("Exception while getting shutdown method, disregarding", it.exception)
            }
        }
    }

    fun registerShutdown(shutdown: CordaFuture<() -> Unit>) {
        state.locked {
            require(!isShutdown)
            registeredShutdowns += shutdown
        }
    }

    fun registerShutdown(shutdown: () -> Unit) = registerShutdown(doneFuture(shutdown))

    fun registerProcessShutdown(process: Process) {
        addShutdownHook { process.destroy() }
        registerShutdown {
            process.destroy()
            /** Wait 5 seconds, then [Process.destroyForcibly] */
            val finishedFuture = executorService.submit {
                process.waitFor()
            }
            try {
                finishedFuture.getOrThrow(5.seconds)
            } catch (timeout: TimeoutException) {
                finishedFuture.cancel(true)
                process.destroyForcibly()
            }
        }
    }

    interface Follower {
        fun unfollow()
        fun shutdown()
    }

    fun follower() = object : Follower {
        private val start = state.locked { registeredShutdowns.size }
        private val end = AtomicInteger(start - 1)
        override fun unfollow() = end.set(state.locked { registeredShutdowns.size })
        override fun shutdown() = end.get().let { end ->
            start > end && throw IllegalStateException("You haven't called unfollow.")
            state.locked {
                registeredShutdowns.subList(start, end).listIterator(end - start).run {
                    while (hasPrevious()) {
                        previous().getOrThrow().invoke()
                        set(doneFuture {}) // Don't break other followers by doing a remove.
                    }
                }
            }
        }
    }

    fun isShuttingDown(): Boolean {
        return state.locked { isShuttingDown }
    }
}