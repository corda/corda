package net.corda.tools.benchmark

import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class LoadGeneratorClient(private val target: String) {
    private val log = LoggerFactory.getLogger(LoadGeneratorClient::class.java)

    fun startTest(parameters: Parameters): Future<Unit> {
        val channelBuilder = ManagedChannelBuilder.forTarget(target).usePlaintext()
        val channel = channelBuilder.build()
        val stub = LoadGeneratorGrpc.newStub(channel)

        val future = CompletableFuture<Unit>()
        val observer = object : StreamObserver<Status> {
            override fun onNext(value: Status) {
                log.info("Load generation started successfully")
            }

            override fun onError(t: Throwable?) {
                log.error("Error starting load test", t)
            }

            override fun onCompleted() {
                future.complete(Unit)
            }
        }
        stub.startTest(parameters, observer)
        return future
    }
}