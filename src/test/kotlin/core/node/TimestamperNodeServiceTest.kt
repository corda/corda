package core.node

import co.paralleluniverse.fibers.Suspendable
import core.*
import core.crypto.SecureHash
import core.node.services.*
import core.protocols.ProtocolLogic
import core.serialization.serialize
import core.testing.MockNetwork
import core.testutils.CASH
import core.utilities.BriefLogFormatter
import org.junit.Before
import org.junit.Test
import protocols.TimestampingProtocol
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimestamperNodeServiceTest {
    lateinit var network: MockNetwork

    init {
        BriefLogFormatter.initVerbose("dlg.timestamping.request")
    }

    val ptx = TransactionBuilder().apply {
        addInputState(StateRef(SecureHash.randomSHA256(), 0))
        addOutputState(100.DOLLARS.CASH)
    }

    val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

    @Before
    fun setup() {
        network = MockNetwork()
    }

    class TestPSM(val server: NodeInfo, val now: Instant) : ProtocolLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            val ptx = TransactionBuilder().apply {
                addInputState(StateRef(SecureHash.randomSHA256(), 0))
                addOutputState(100.DOLLARS.CASH)
            }
            ptx.addCommand(TimestampCommand(now - 20.seconds, now + 20.seconds), server.identity.owningKey)
            val wtx = ptx.toWireTransaction()
            // This line will invoke sendAndReceive to interact with the network.
            val sig = subProtocol(TimestampingProtocol(server, wtx.serialized))
            ptx.checkAndAddSignature(sig)
            return true
        }
    }

    @Test
    fun successWithNetwork() {
        val timestamperNode = network.createNode(null, advertisedServices = TimestamperService.Type)
        val logName = NodeTimestamperService.TIMESTAMPING_PROTOCOL_TOPIC
        val psm = TestPSM(timestamperNode.info, clock.instant())
        val future = timestamperNode.smm.add(logName, psm)

        network.runNetwork()
        assertTrue(future.isDone)
    }

    @Test
    fun wrongCommands() {
        val timestamperNode = network.createNode(null, advertisedServices = TimestamperService.Type)
        val timestamperKey = timestamperNode.services.storageService.myLegalIdentity.owningKey
        val service = timestamperNode.inNodeTimestampingService!!
        
        // Zero commands is not OK.
        assertFailsWith(TimestampingError.RequiresExactlyOneCommand::class) {
            val wtx = ptx.toWireTransaction()
            service.processRequest(TimestampingProtocol.Request(wtx.serialize(), timestamperNode.info.address, Long.MIN_VALUE))
        }
        // More than one command is not OK.
        assertFailsWith(TimestampingError.RequiresExactlyOneCommand::class) {
            ptx.addCommand(TimestampCommand(clock.instant(), 30.seconds), timestamperKey)
            ptx.addCommand(TimestampCommand(clock.instant(), 40.seconds), timestamperKey)
            val wtx = ptx.toWireTransaction()
            service.processRequest(TimestampingProtocol.Request(wtx.serialize(), timestamperNode.info.address, Long.MIN_VALUE))
        }
    }

    @Test
    fun tooEarly() {
        val timestamperNode = network.createNode(null, advertisedServices = TimestamperService.Type)
        val timestamperKey = timestamperNode.services.storageService.myLegalIdentity.owningKey
        val service = timestamperNode.inNodeTimestampingService!!

        assertFailsWith(TimestampingError.NotOnTimeException::class) {
            val now = clock.instant()
            ptx.addCommand(TimestampCommand(now - 60.seconds, now - 40.seconds), timestamperKey)
            val wtx = ptx.toWireTransaction()
            service.processRequest(TimestampingProtocol.Request(wtx.serialize(), timestamperNode.info.address, Long.MIN_VALUE))
        }
    }

    @Test
    fun tooLate() {
        val timestamperNode = network.createNode(null, advertisedServices = TimestamperService.Type)
        val timestamperKey = timestamperNode.services.storageService.myLegalIdentity.owningKey
        val service = timestamperNode.inNodeTimestampingService!!

        assertFailsWith(TimestampingError.NotOnTimeException::class) {
            val now = clock.instant()
            ptx.addCommand(TimestampCommand(now - 60.seconds, now - 40.seconds), timestamperKey)
            val wtx = ptx.toWireTransaction()
            service.processRequest(TimestampingProtocol.Request(wtx.serialize(), timestamperNode.info.address, Long.MIN_VALUE))
        }
    }

    @Test
    fun success() {
        val timestamperNode = network.createNode(null, advertisedServices = TimestamperService.Type)
        val timestamperKey = timestamperNode.services.storageService.myLegalIdentity.owningKey
        val service = timestamperNode.inNodeTimestampingService!!

        val now = clock.instant()
        ptx.addCommand(TimestampCommand(now - 20.seconds, now + 20.seconds), timestamperKey)
        val wtx = ptx.toWireTransaction()
        val sig = service.processRequest(TimestampingProtocol.Request(wtx.serialize(), timestamperNode.info.address, Long.MIN_VALUE))
        ptx.checkAndAddSignature(sig)
        ptx.toSignedTransaction(false).verifySignatures()
    }
}
