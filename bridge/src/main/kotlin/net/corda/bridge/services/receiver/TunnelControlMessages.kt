package net.corda.bridge.services.receiver

import net.corda.bridge.services.receiver.FloatControlTopics.FLOAT_CONTROL_TOPIC
import net.corda.bridge.services.receiver.FloatControlTopics.FLOAT_DATA_TOPIC
import net.corda.bridge.services.receiver.FloatControlTopics.FLOAT_SIGNING_TOPIC
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.minutes
import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@CordaSerializable
sealed class TunnelControlMessage

object FloatControlTopics {
    const val FLOAT_CONTROL_TOPIC = "float.control"
    const val FLOAT_SIGNING_TOPIC = "float.signing"
    const val FLOAT_DATA_TOPIC = "float.forward"
}

internal class ActivateFloat(val certificates: Map<String, List<X509Certificate>>, val trustStoreBytes: ByteArray, val trustStorePassword: CharArray, val maxMessageSize: Int, val bridgeCommTimeout: Duration = 1.minutes) : TunnelControlMessage()

internal abstract class TunnelControlMessageWithId(val requestId: Long) : TunnelControlMessage()

private val requestCounter = AtomicLong(System.currentTimeMillis()) // Initialise once with current ms value to avoid clash from previous start-ups

internal class SigningRequest(requestId: Long = requestCounter.incrementAndGet(), val alias: String, val sigAlgo: String, val data: ByteArray) : TunnelControlMessageWithId(requestId)
internal class SigningResponse(requestId: Long, val signature: ByteArray?) : TunnelControlMessageWithId(requestId)

object DeactivateFloat : TunnelControlMessage()

fun ReceivedMessage.checkTunnelControlTopic() = (topic == FLOAT_CONTROL_TOPIC || topic == FLOAT_SIGNING_TOPIC)

@CordaSerializable
internal class FloatDataPacket(val topic: String,
                               val originalHeaders: List<Pair<String, Any?>>,
                               val originalPayload: ByteArray,
                               val sourceLegalName: CordaX500Name,
                               val sourceLink: NetworkHostAndPort,
                               val destinationLegalName: CordaX500Name,
                               val destinationLink: NetworkHostAndPort)

fun ReceivedMessage.checkTunnelDataTopic() = (topic == FLOAT_DATA_TOPIC)
