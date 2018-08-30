package net.corda.tools.benchmark

import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

fun main(args: Array<String>) {
    CommandLine.run(BenchmarkTool(), System.err, *args)
}

class BenchmarkTool : Runnable {
    @Option(names = ["-listen", "-l"], description = ["Start the load generator service"])
    var listenPort: Int? = null

    @Option(names = ["-host", "-h"], description = ["Send a command to the remote load generator service"])
    var serverAddress: String? = null

    @Parameters(index = "0", arity = "0..1")
    var rateTps: Int = 1

    @Parameters(index = "1", arity = "0..1")
    var transactionSizeBytes: Int = 100

    @Parameters(index = "2", arity = "0..1")
    var transactionProcessingTimeMs: Int = 10

    @Option(names = ["--verbose"], description = ["Enable verbose output"])
    var verbose: Boolean = false

    private val log = LoggerFactory.getLogger(BenchmarkTool::class.java)

    override fun run() {
        if (verbose) {
            System.setProperty("logLevel", "trace")
        }

        if (listenPort != null) {
            log.info("Starting load generator server on port $listenPort")
            LoadGeneratorServer(listenPort!!).start()

        } else if (serverAddress != null) {
            log.info("Connecting to load generator server at $serverAddress")
            // Send command
            val sender = LoadGeneratorClient(serverAddress!!)
            val params = net.corda.tools.benchmark.Parameters.newBuilder()
                    .setRateTps(rateTps)
                    .setTransactionSizeByes(transactionSizeBytes)
                    .setTransactionProcessingTimeMs(transactionProcessingTimeMs)
                    .build()
            log.info("Starting load generation with parameters: \n$params")
            sender.startTest(params).get()
        } else {
            throw IllegalArgumentException("Invalid parameters specified")
        }
    }
}


