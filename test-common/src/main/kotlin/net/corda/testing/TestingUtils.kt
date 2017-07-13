package net.corda.testing

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.concurrent.firstOf
import net.corda.core.getOrThrow
import net.corda.core.millis
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.config.SSLConfiguration
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("net.corda.testing")

class ListenProcessDeathException(hostAndPort: NetworkHostAndPort, listenProcess: Process) : Exception("The process that was expected to listen on $hostAndPort has died with status: ${listenProcess.exitValue()}")

/** @param hostAndPort an address on which the process is listening, for the failure message. */
fun ScheduledExecutorService.pollProcessDeath(process: Process, hostAndPort: NetworkHostAndPort) = poll("process death") {
    if (process.isAlive) null else ListenProcessDeathException(hostAndPort, process)
}

fun <A> ScheduledExecutorService.poll(
        pollName: String,
        pollInterval: Duration = 500.millis,
        warnCount: Int = 120,
        check: () -> A?
): ListenableFuture<A> {
    val resultFuture = SettableFuture.create<A>()
    val task = object : Runnable {
        var counter = -1
        override fun run() {
            if (resultFuture.isCancelled) return // Give up, caller can no longer get the result.
            if (++counter == warnCount) {
                log.warn("Been polling $pollName for ${pollInterval.multipliedBy(warnCount.toLong()).seconds} seconds...")
            }
            try {
                val checkResult = check()
                if (checkResult != null) {
                    resultFuture.set(checkResult)
                } else {
                    schedule(this, pollInterval.toMillis(), TimeUnit.MILLISECONDS)
                }
            } catch (t: Throwable) {
                resultFuture.setException(t)
            }
        }
    }
    submit(task) // The check may be expensive, so always run it in the background even the first time.
    return resultFuture
}

/** @param sslConfig optional [CordaRPCClient] arg. */
fun ScheduledExecutorService.establishRpc(nodeAddress: NetworkHostAndPort, sslConfig: SSLConfiguration?, username: String, password: String, processDeathFuture: ListenableFuture<out Throwable>): ListenableFuture<Pair<CordaRPCClient, CordaRPCConnection>> {
    val client = CordaRPCClient(nodeAddress, sslConfig)
    val connectionFuture = poll("RPC connection") {
        try {
            client.start(username, password)
        } catch (e: Exception) {
            if (processDeathFuture.isDone) throw e
            log.error("Exception $e, Retrying RPC connection at $nodeAddress")
            null
        }
    }
    return firstOf(connectionFuture, processDeathFuture) {
        if (it == processDeathFuture) throw processDeathFuture.getOrThrow()
        Pair(client, connectionFuture.getOrThrow())
    }
}

/** Does not use interrupt, which is unreliable in general as most tasks don't interrupt well. Effectively we assert this executor is idle. */
fun ExecutorService.shutdownAndAwaitTermination() {
    shutdown()
    while (!awaitTermination(1, TimeUnit.SECONDS)) {
        // Do nothing.
    }
}
