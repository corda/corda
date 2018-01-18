package net.corda.testing.node.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.internal.ThreadBox
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class ShutdownManager(private val executorService: ExecutorService) {
    private class State {
        val registeredShutdowns = ArrayList<Pair<String, CordaFuture<() -> Unit>>>()
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
        val shutdownActionFutures = state.locked {
            if (isShutdown) {
                emptyList<Pair<String, CordaFuture<() -> Unit>>>()
            } else {
                isShutdown = true
                registeredShutdowns
            }
        }

        val shutdowns = shutdownActionFutures.map { (description, future) ->
            if (!future.isDone) {
                log.info("Waiting for shutdown task $description ...")
            }
            Pair(description, Try.on { future.getOrThrow() })
        }
        shutdowns.reversed().forEach { (description, result) ->
            when (result) {
                is Try.Success ->
                    try {
                        result.value()
                    } catch (t: Throwable) {
                        log.warn("Exception while executing a shutdown task $description", t)
                    }
                is Try.Failure -> log.warn("Disregarding exception thrown while waiting for shutdown task $description", result.exception)
            }
        }
    }

    fun registerShutdown(description: String, shutdown: CordaFuture<() -> Unit>) {
        state.locked {
            require(!isShutdown)
            registeredShutdowns += Pair(description, shutdown)
        }
    }

    fun registerShutdown(description: String, shutdown: () -> Unit) = registerShutdown(description, doneFuture(shutdown))

    fun registerProcessShutdown(description: String, process: Process) {
        registerShutdown(description) {
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
                        val (description, future) = previous()
                        future.getOrThrow().invoke()
                        set(Pair(description, doneFuture {})) // Don't break other followers by doing a remove.
                    }
                }
            }
        }
    }
}