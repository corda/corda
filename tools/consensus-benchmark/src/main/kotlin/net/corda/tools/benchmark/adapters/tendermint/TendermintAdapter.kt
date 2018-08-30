package net.corda.tools.benchmark.adapters.tendermint

import com.github.jtendermint.jabci.api.*
import com.github.jtendermint.jabci.socket.TSocket
import com.github.jtendermint.jabci.types.*
import com.google.common.base.Stopwatch
import com.google.protobuf.ByteString
import net.corda.tools.benchmark.Adapter
import org.bouncycastle.util.encoders.Hex
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread


class TendermintAdapter : Adapter() {
    private val log: Logger get() = LoggerFactory.getLogger(TendermintAdapter::class.java)

    private val inFlight = ConcurrentHashMap<String, CompletableFuture<Unit>>()
    private val stateMachine = TendermintStateMachine(inFlight)
    private val tendermintClient: TendermintClient = TendermintClient().apply {
        start()
    }

    override val metricPrefix: String
        get() = "Tendermint"

    /** Transaction commit duration and rate metric timer */
    private val commitTimer = metrics.timer("$metricPrefix.Commit")


    override fun submitTransaction(tx: ByteArray): Future<Unit> {
        val future = CompletableFuture<Unit>()
        val hash = hashString(tx)
        log.info("Submitting transaction $hash")
        inFlight[hash] = future
        val totalTime = Stopwatch.createStarted()

        future.thenRun {
            totalTime.stop()
            val elapsed = totalTime.elapsed(TimeUnit.MILLISECONDS)
            commitTimer.update(elapsed, TimeUnit.MILLISECONDS)
            log.info("Transaction complete: $elapsed Ms")
        }

        tendermintClient.sendRecord(tx)
        return future
    }
}

class TendermintStateMachine(private val inFlight: ConcurrentHashMap<String, CompletableFuture<Unit>>) : IDeliverTx, ICheckTx, ICommit, IBeginBlock {
    companion object {
        private const val DEFAULT_PORT = 26658
    }

    private val log: Logger get() = LoggerFactory.getLogger(TendermintStateMachine::class.java)

    private val state = AtomicLong(0)
    private val socketListener: Thread

    init {
        socketListener = thread(name = "Tendermint listener") {
            // Start socket
            val socket = TSocket()
            socket.registerListener(this)
            log.info("Listening on $DEFAULT_PORT")
            socket.start(DEFAULT_PORT)
        }
    }

    /** Generates a hash of the current state. */
    override fun requestCommit(requestCommit: RequestCommit): ResponseCommit {
        val buff = ByteBuffer.allocate(8).putLong(state.incrementAndGet())
        val digest = MessageDigest.getInstance("SHA-256")
        val encodedHash = digest.digest(buff.array())
        log.info("Generated commit hash: ${Hex.toHexString(encodedHash)}")

        return ResponseCommit.newBuilder().setData(ByteString.copyFrom(encodedHash)).build()
    }

    override fun receivedDeliverTx(req: RequestDeliverTx): ResponseDeliverTx {
        val tx = req.tx.toByteArray()
        val hash = hashString(tx)
        val future = inFlight[hash]
        if (future != null) {
            future.complete(Unit)
            inFlight.remove(hash)
        } else {
            log.warn("Transaction $hash not found in the inFlight map")
        }

        return ResponseDeliverTx.newBuilder().setCode(CodeType.OK).build()
    }

    override fun requestCheckTx(req: RequestCheckTx?): ResponseCheckTx {
        return ResponseCheckTx.getDefaultInstance()
    }

    override fun requestBeginBlock(req: RequestBeginBlock): ResponseBeginBlock {
        return ResponseBeginBlock.getDefaultInstance()
    }
}

fun hashString(payload: ByteArray): String {
    val md = MessageDigest.getInstance("MD5")
    md.update(payload)
    val digest = md.digest()
    return Hex.toHexString(digest)
}