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

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.identity.CordaX500Name
import net.corda.node.services.statemachine.DeduplicationId
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.apache.mina.util.ConcurrentHashSet
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

/**
 * Encapsulate the de-duplication logic.
 */
class P2PMessageDeduplicator(private val database: CordaPersistence) {
    val ourSenderUUID = UUID.randomUUID().toString()

    // A temporary in-memory set of deduplication IDs. When we receive a message we don't persist the ID immediately,
    // so we store the ID here in the meantime (until the persisting db tx has committed). This is because Artemis may
    // redeliver messages to the same consumer if they weren't ACKed.
    private val beingProcessedMessages = ConcurrentHashSet<DeduplicationId>()
    private val processedMessages = createProcessedMessages()
    // We add the peer to the key, so other peers cannot attempt malicious meddling with sequence numbers.
    // Expire after 7 days since we last touched an entry, to avoid infinite growth.
    private val senderUUIDSeqNoHWM: MutableMap<Triple<String, CordaX500Name, Boolean>, Long> = Caffeine.newBuilder().expireAfterAccess(7, TimeUnit.DAYS).build<Triple<String, CordaX500Name, Boolean>, Long>().asMap()

    private fun createProcessedMessages(): AppendOnlyPersistentMap<DeduplicationId, Instant, ProcessedMessage, String> {
        return AppendOnlyPersistentMap(
                toPersistentEntityKey = { it.toString },
                fromPersistentEntity = { Pair(DeduplicationId(it.id), it.insertionTime) },
                toPersistentEntity = { key: DeduplicationId, value: Instant ->
                    ProcessedMessage().apply {
                        id = key.toString
                        insertionTime = value
                    }
                },
                persistentEntityClass = ProcessedMessage::class.java
        )
    }

    private fun isDuplicateInDatabase(msg: ReceivedMessage): Boolean = database.transaction { msg.uniqueMessageId in processedMessages }

    /**
     * We assign the sender a random identifier [ourSenderUUID] (passed to [MessagingExecutor]). If the sender is also the creator of a message
     * (i.e. not from recovered checkpoints), assign a sequence number. Recipients know it is not a duplicate if the sequence number
     * is greater than the highest they have seen, otherwise fallback to prior, slower, logic within the database.
     *
     * The UUIDs will change each time the sender restarts their JVM, and so we may need to prune UUIDs over time. To this end,
     * we only remember UUIDs for 7 days from the time we last interacted with them, rebuilding the cached value if we
     * ever re-encounter them.
     *
     * We also ensure the UUID cannot be spoofed, by incorporating the authenticated sender into the key of the map/cache.
     */
    private fun isDuplicateWithPotentialOptimization(receivedSenderUUID: String, receivedSenderSeqNo: Long, msg: ReceivedMessage): Boolean {
        return senderUUIDSeqNoHWM.compute(Triple(receivedSenderUUID, msg.peer, msg.isSessionInit)) { key, existingSeqNoHWM ->
            val isNewHWM = (existingSeqNoHWM != null && existingSeqNoHWM < receivedSenderSeqNo)
            if (isNewHWM) {
                // If we are the new HWM, set the HWM to us.
                receivedSenderSeqNo
            } else {
                // If we are a duplicate, unset the HWM, since it seems like re-delivery is happening for traffic from that sender.
                // else if we are not a duplicate, (re)set the HWM to us.
                if (isDuplicateInDatabase(msg)) null else receivedSenderSeqNo
            }
        } != receivedSenderSeqNo
    }

    /**
     * @return true if we have seen this message before.
     */
    fun isDuplicate(msg: ReceivedMessage): Boolean {
        if (msg.uniqueMessageId in beingProcessedMessages) {
            return true
        }
        val receivedSenderUUID = msg.senderUUID
        val receivedSenderSeqNo = msg.senderSeqNo
        // If we have received a new higher sequence number, then it cannot be a duplicate, and we don't need to check database.
        // If we are seeing a sender for the first time, fall back to a database check.
        // If we have no information about the sender, also fall back to a database check.
        return if (receivedSenderUUID != null && receivedSenderSeqNo != null) {
            isDuplicateWithPotentialOptimization(receivedSenderUUID, receivedSenderSeqNo, msg)
        } else {
            isDuplicateInDatabase(msg)
        }
    }

    /**
     * Called the first time we encounter [deduplicationId].
     */
    fun signalMessageProcessStart(deduplicationId: DeduplicationId) {
        beingProcessedMessages.add(deduplicationId)
    }

    /**
     * Called inside a DB transaction to persist [deduplicationId].
     */
    fun persistDeduplicationId(deduplicationId: DeduplicationId) {
        processedMessages[deduplicationId] = Instant.now()
    }

    /**
     * Called after the DB transaction persisting [deduplicationId] committed.
     * Any subsequent redelivery will be deduplicated using the DB.
     */
    fun signalMessageProcessFinish(deduplicationId: DeduplicationId) {
        beingProcessedMessages.remove(deduplicationId)
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}message_ids")
    class ProcessedMessage(
            @Id
            @Column(name = "message_id", length = 64)
            var id: String = "",

            @Column(name = "insertion_time")
            var insertionTime: Instant = Instant.now()
    )
}