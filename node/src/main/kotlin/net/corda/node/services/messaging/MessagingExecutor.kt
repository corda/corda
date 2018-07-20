/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.messaging

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.SettableFuture
import com.codahale.metrics.MetricRegistry
import net.corda.core.messaging.MessageRecipients
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import net.corda.node.VersionInfo
import net.corda.node.services.statemachine.FlowMessagingImpl
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2PMessagingHeaders
import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
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
        metricRegistry: MetricRegistry,
        val ourSenderUUID: String,
        queueBound: Int,
        val myLegalName: String
) {
    private sealed class Job {
        data class Acknowledge(val message: ClientMessage) : Job()
        data class Send(
                val message: Message,
                val target: MessageRecipients,
                val sentFuture: SettableFuture<Unit>
        ) : Job() {
            override fun toString() = "Send(${message.uniqueMessageId}, target=$target)"
        }
        object Shutdown : Job() { override fun toString() = "Shutdown" }
    }

    private val queue = ArrayBlockingQueue<Job>(queueBound)
    private var executor: Thread? = null
    private val cordaVendor = SimpleString(versionInfo.vendor)
    private val releaseVersion = SimpleString(versionInfo.releaseVersion)
    private val sendMessageSizeMetric = metricRegistry.histogram("SendMessageSize")
    private val sendLatencyMetric = metricRegistry.timer("SendLatency")
    private val ourSenderSeqNo = AtomicLong()

    private companion object {
        val log = contextLogger()
        val amqDelayMillis = System.getProperty("amq.delivery.delay.ms", "0").toInt()
    }

    /**
     * Submit a send job of [message] to [target] and wait until it finishes.
     * This call may yield the fiber.
     */
    @Suspendable
    fun send(message: Message, target: MessageRecipients) {
        val sentFuture = SettableFuture<Unit>()
        val job = Job.Send(message, target, sentFuture)
        val context = sendLatencyMetric.time()
        try {
            queue.put(job)
            sentFuture.get()
        } catch (executionException: ExecutionException) {
            throw executionException.cause!!
        } finally {
            context.stop()
        }
    }

    /**
     * Submit an acknowledge job of [message].
     * This call does NOT wait for confirmation of the ACK receive. If a failure happens then the message will either be
     * redelivered, deduped and acked, or the message was actually acked before failure in which case all is good.
     */
    fun acknowledge(message: ClientMessage) {
        queue.put(Job.Acknowledge(message))
    }

    fun start() {
        require(executor == null)
        executor = thread(name = "Messaging executor", isDaemon = true) {
            eventLoop@ while (true) {
                val job = queue.take() // Block until at least one job is available.
                try {
                    when (job) {
                        is Job.Acknowledge -> {
                            acknowledgeJob(job)
                        }
                        is Job.Send -> {
                            try {
                                sendJob(job)
                            } catch (duplicateException: ActiveMQDuplicateIdException) {
                                log.warn("Message duplication", duplicateException)
                                job.sentFuture.set(Unit)
                            }
                        }
                        Job.Shutdown -> {
                            session.commit()
                            break@eventLoop
                        }
                    }
                } catch (exception: ActiveMQObjectClosedException) {
                    log.error("Messaging client connection closed", exception)
                    if (job is Job.Send) {
                        job.sentFuture.setException(exception)
                    }
                    System.exit(1)
                } catch (exception: Throwable) {
                    log.error("Exception while handling job $job, disregarding", exception)
                    if (job is Job.Send) {
                        job.sentFuture.setException(exception)
                    }
                }
            }
        }
    }

    fun close() {
        val executor = this.executor
        if (executor != null) {
            queue.offer(Job.Shutdown)
            executor.join()
            this.executor = null
        }
    }

    private fun sendJob(job: Job.Send) {
        val mqAddress = resolver.resolveTargetToArtemisQueue(job.target)
        val artemisMessage = cordaToArtemisMessage(job.message)
        log.trace {
            "Send to: $mqAddress topic: ${job.message.topic} " +
                    "sessionID: ${job.message.topic} id: ${job.message.uniqueMessageId}"
        }
        producer.send(SimpleString(mqAddress), artemisMessage, { job.sentFuture.set(Unit) })
    }

    fun cordaToArtemisMessage(message: Message): ClientMessage? {
        return session.createMessage(true).apply {
            putStringProperty(P2PMessagingHeaders.cordaVendorProperty, cordaVendor)
            putStringProperty(P2PMessagingHeaders.releaseVersionProperty, releaseVersion)
            putIntProperty(P2PMessagingHeaders.platformVersionProperty, versionInfo.platformVersion)
            putStringProperty(P2PMessagingHeaders.topicProperty, SimpleString(message.topic))
            putStringProperty(P2PMessagingHeaders.bridgedCertificateSubject, SimpleString(myLegalName))
            sendMessageSizeMetric.update(message.data.bytes.size)
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

    private fun acknowledgeJob(job: Job.Acknowledge) {
        log.debug {
            val id = job.message.getStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID)
            "Acking $id"
        }
        job.message.individualAcknowledge()
    }
}
