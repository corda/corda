package net.corda.notarytest

import com.google.common.base.Stopwatch
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.notarytest.service.JDBCLoadTestFlow
import java.io.File
import java.io.PrintWriter
import java.time.Instant
import java.util.concurrent.TimeUnit

/** The number of test flows to run on each notary node */
const val TEST_RUNS = 1
/** Total number of transactions to generate and notarise. */
const val TRANSACTION_COUNT = 10000000
/** Number of transactions to submit before awaiting completion. */
const val BATCH_SIZE = 1000

fun main(args: Array<String>) {
    // Provide a list of notary node addresses to invoke the load generation flow on
    val addresses = listOf(
            NetworkHostAndPort("localhost", 10010),
            NetworkHostAndPort("localhost", 11014),
            NetworkHostAndPort("localhost", 11018)
    )

    addresses.parallelStream().forEach {
        val node = it
        println("Connecting to the recipient node ($node)")

        CordaRPCClient(it).start(notaryDemoUser.username, notaryDemoUser.password).use {
            println(it.proxy.nodeInfo())
            val totalTime = Stopwatch.createStarted()
            val durations = run(it.proxy, 1)
            totalTime.stop()

            val totalTx = TEST_RUNS * TRANSACTION_COUNT
            println("Total duration for $totalTx transactions: ${totalTime.elapsed(TimeUnit.MILLISECONDS)} ms")
            println("Average tx/s: ${totalTx.toDouble() / totalTime.elapsed(TimeUnit.MILLISECONDS).toDouble() * 1000}")

            // Uncomment to generate a CSV report
            // printCSV(node, durations, TEST_RUNS, BATCH_SIZE)
        }
    }
}

private fun run(rpc: CordaRPCOps, inputStateCount: Int? = null): List<Long> {
    return (1..TEST_RUNS).map { i ->
        val timer = Stopwatch.createStarted()
        val commitDuration = rpc.startFlow(::JDBCLoadTestFlow, TRANSACTION_COUNT, BATCH_SIZE, inputStateCount).returnValue.get()
        val flowDuration = timer.stop().elapsed(TimeUnit.MILLISECONDS)
        println("#$i: Duration: $flowDuration ms, commit duration: $commitDuration ms")
        flowDuration
    }
}

private fun printCSV(node: NetworkHostAndPort, durations: List<Long>, testRuns: Int, batchSize: Int) {
    val pw = PrintWriter(File("notarytest-${Instant.now()}-${node.host}${node.port}-${testRuns}x$batchSize.csv"))
    val sb = StringBuilder()
    sb.append("$testRuns, $batchSize")
    sb.append('\n')
    sb.append(durations.joinToString())
    pw.write(sb.toString())
    pw.close()
}