package net.corda.tools.benchmark

import com.google.common.util.concurrent.RateLimiter
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import net.corda.tools.benchmark.adapters.tendermint.TendermintAdapter
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.thread

private val log = LoggerFactory.getLogger("Generator")

class LoadGeneratorServer(private val listenPort: Int) {
    fun start() {
        val builder = ServerBuilder.forPort(listenPort)
        val server = builder
                .addService(LoadGenerator())
                .build()
        server.start()
        log.info("Starting load generator server at port $listenPort")

        while (!Thread.interrupted()) {
            Thread.sleep(10000)
        }
    }
}

class LoadGenerator : LoadGeneratorGrpc.LoadGeneratorImplBase() {
    private var generator: Generator? = null
    private var generatorThread: Thread? = null
    private val adapter: Adapter

    init {
        // TODO: dynamically load adapter
        adapter = TendermintAdapter()
    }

    override fun startTest(request: Parameters, responseObserver: StreamObserver<Status>) {
        if (generator != null) {
            generator!!.stop()
            generatorThread!!.join()

        }
        generator = Generator(adapter, request)
        generatorThread = thread { generator!!.run() }

        val response = Status.newBuilder().build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    private class Generator(private val adapter: Adapter, private val testParameters: Parameters) : Runnable {
        private var running: Boolean = true
        fun stop() {
            log.info("Stopping load generation")
            running = false
        }

        override fun run() {
            log.info("Generating load with parameters:\n$testParameters")
            val random = Random()
            val rateLimiter = RateLimiter.create(testParameters.rateTps.toDouble())
            while (running) {
                rateLimiter.acquire()
                val payload = generatePayload(testParameters, random)
                adapter.submitTransaction(payload)
            }
        }

        private fun generatePayload(parameters: Parameters, random: Random): ByteArray {
//            val simulatedProcessingTime = parameters

            val bytes = ByteArray(parameters.transactionSizeByes)
            random.nextBytes(bytes)
            return bytes
        }
    }
}
