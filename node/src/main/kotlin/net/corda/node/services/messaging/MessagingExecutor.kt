package net.corda.node.services.messaging

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.SettableFuture
import com.codahale.metrics.MetricRegistry
import net.corda.core.messaging.MessageRecipients
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.VersionInfo
import net.corda.node.services.statemachine.FlowMessagingImpl
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

interface AddressToArtemisQueueResolver {
    /**
     * Resolves a [MessageRecipients] to an Artemis queue name, creating the underlying queue if needed.
     */
    fun resolveTargetToArtemisQueue(address: MessageRecipients): String
}

/**
 * The [MessagingExecutor] is responsible for handling send and acknowledge jobs. It batches them using a bounded
 * blocking queue, submits the jobs asynchronously and then waits for them to flush using [ClientSession.commit].
 * Note that even though we buffer in theory this shouldn't increase latency as the executor is immediately woken up if
 * it was waiting. The number of jobs in the queue is only ever greater than 1 if the commit takes a long time.
 */
class MessagingExecutor(
        val session: ClientSession,
        val producer: ClientProducer,
        val versionInfo: VersionInfo,
        val resolver: AddressToArtemisQueueResolver,
        val ourSenderUUID: String
) {
    private val cordaVendor = SimpleString(versionInfo.vendor)
    private val releaseVersion = SimpleString(versionInfo.releaseVersion)
    private val ourSenderSeqNo = AtomicLong()

    private companion object {
        val log = contextLogger()
        val amqDelayMillis = System.getProperty("amq.delivery.delay.ms", "0").toInt()
    }

    fun send(message: Message, target: MessageRecipients) {
        val mqAddress = resolver.resolveTargetToArtemisQueue(target)
        val artemisMessage = cordaToArtemisMessage(message)
        log.trace {
            "Send to: $mqAddress topic: ${message.topic} " +
                    "sessionID: ${message.topic} id: ${message.uniqueMessageId}"
        }
        producer.send(SimpleString(mqAddress), artemisMessage)
    }

    fun acknowledge(message: ClientMessage) {
        message.individualAcknowledge()
     }

    internal fun cordaToArtemisMessage(message: Message): ClientMessage? {
        return session.createMessage(true).apply {
            putStringProperty(P2PMessagingHeaders.cordaVendorProperty, cordaVendor)
            putStringProperty(P2PMessagingHeaders.releaseVersionProperty, releaseVersion)
            putIntProperty(P2PMessagingHeaders.platformVersionProperty, versionInfo.platformVersion)
            putStringProperty(P2PMessagingHeaders.topicProperty, SimpleString(message.topic))
            writeBodyBufferBytes(message.data.bytes)
            // Use the magic deduplication property built into Artemis as our message identity too
            putStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID, SimpleString(message.uniqueMessageId.toString))
            // If we are the sender (ie. we are not going through recovery of some sort), use sequence number short cut.
            if (ourSenderUUID == message.senderUUID) {
                putStringProperty(P2PMessagingHeaders.senderUUID, SimpleString(ourSenderUUID))
                putLongProperty(P2PMessagingHeaders.senderSeqNo, ourSenderSeqNo.getAndIncrement())
            }
            // For demo purposes - if set then add a delay to messages in order to demonstrate that the flows are doing as intended
            if (amqDelayMillis > 0 && message.topic == FlowMessagingImpl.sessionTopic) {
                putLongProperty(org.apache.activemq.artemis.api.core.Message.HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + amqDelayMillis)
            }
            message.additionalHeaders.forEach { key, value -> putStringProperty(key, value) }
        }
    }
}
