package net.corda.node.services.messaging

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.SettableFuture
import com.codahale.metrics.MetricRegistry
import net.corda.core.messaging.MessageRecipients
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.VersionInfo
import net.corda.node.services.statemachine.FlowMessagingImpl
import org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException
import org.apache.activemq.artemis.api.core.ActiveMQException
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
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
        queueBound: Int
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
    private val sendBatchSizeMetric = metricRegistry.histogram("SendBatchSize")

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
            val batch = ArrayList<Job>()
            eventLoop@ while (true) {
                batch.add(queue.take()) // Block until at least one job is available.
                queue.drainTo(batch)
                sendBatchSizeMetric.update(batch.filter { it is Job.Send }.size)
                val shouldShutdown = try {
                    // Try to handle the batch in one commit.
                    handleBatchTransactional(batch)
                } catch (exception: ActiveMQException) {
                    // A job failed, rollback and do it one at a time, simply log and skip if an individual job fails.
                    // If a send job fails the exception will be re-raised in the corresponding future.
                    // Note that this fallback assumes that there are no two jobs in the batch that depend on one
                    // another. As the exception is re-raised in the requesting calling thread in case of a send, we can
                    // assume no "in-flight" messages will be sent out of order after failure.
                    log.warn("Exception while handling transactional batch, falling back to handling one job at a time", exception)
                    handleBatchOneByOne(batch)
                }
                batch.clear()
                if (shouldShutdown) {
                    break@eventLoop
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

    /**
     * Handles a batch of jobs in one transaction.
     * @return true if the executor should shut down, false otherwise.
     * @throws ActiveMQException
     */
    private fun handleBatchTransactional(batch: List<Job>): Boolean {
        for (job in batch) {
            when (job) {
                is Job.Acknowledge -> {
                    acknowledgeJob(job)
                }
                is Job.Send -> {
                    sendJob(job)
                }
                Job.Shutdown -> {
                    session.commit()
                    return true
                }
            }
        }
        session.commit()
        return false
    }

    /**
     * Handles a batch of jobs one by one, committing after each.
     * @return true if the executor should shut down, false otherwise.
     */
    private fun handleBatchOneByOne(batch: List<Job>): Boolean {
        for (job in batch) {
            try {
                when (job) {
                    is Job.Acknowledge -> {
                        acknowledgeJob(job)
                        session.commit()
                    }
                    is Job.Send -> {
                        try {
                            sendJob(job)
                            session.commit()
                        } catch (duplicateException: ActiveMQDuplicateIdException) {
                            log.warn("Message duplication", duplicateException)
                            job.sentFuture.set(Unit)
                        }
                    }
                    Job.Shutdown -> {
                        session.commit()
                        return true
                    }
                }
            } catch (exception: Throwable) {
                log.error("Exception while handling job $job, disregarding", exception)
                if (job is Job.Send) {
                    job.sentFuture.setException(exception)
                }
                session.rollback()
            }
        }
        return false
    }

    private fun sendJob(job: Job.Send) {
        val mqAddress = resolver.resolveTargetToArtemisQueue(job.target)
        val artemisMessage = session.createMessage(true).apply {
            putStringProperty(P2PMessagingClient.cordaVendorProperty, cordaVendor)
            putStringProperty(P2PMessagingClient.releaseVersionProperty, releaseVersion)
            putIntProperty(P2PMessagingClient.platformVersionProperty, versionInfo.platformVersion)
            putStringProperty(P2PMessagingClient.topicProperty, SimpleString(job.message.topic))
            sendMessageSizeMetric.update(job.message.data.bytes.size)
            writeBodyBufferBytes(job.message.data.bytes)
            // Use the magic deduplication property built into Artemis as our message identity too
            putStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_DUPLICATE_DETECTION_ID, SimpleString(job.message.uniqueMessageId.toString))

            // For demo purposes - if set then add a delay to messages in order to demonstrate that the flows are doing as intended
            if (amqDelayMillis > 0 && job.message.topic == FlowMessagingImpl.sessionTopic) {
                putLongProperty(org.apache.activemq.artemis.api.core.Message.HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + amqDelayMillis)
            }
        }
        log.trace {
            "Send to: $mqAddress topic: ${job.message.topic} " +
                    "sessionID: ${job.message.topic} id: ${job.message.uniqueMessageId}"
        }
        producer.send(SimpleString(mqAddress), artemisMessage) { job.sentFuture.set(Unit) }
    }

    private fun acknowledgeJob(job: Job.Acknowledge) {
        job.message.individualAcknowledge()
    }
}