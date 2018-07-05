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
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.node.services.statemachine.DeduplicationId
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

typealias SenderHashToSeqNo = Pair<String, Long?>

/**
 * Encapsulate the de-duplication logic.
 */
class P2PMessageDeduplicator(private val database: CordaPersistence) {
    // A temporary in-memory set of deduplication IDs and associated high water mark details.
    // When we receive a message we don't persist the ID immediately,
    // so we store the ID here in the meantime (until the persisting db tx has committed). This is because Artemis may
    // redeliver messages to the same consumer if they weren't ACKed.
    private val beingProcessedMessages = ConcurrentHashMap<DeduplicationId, MessageMeta>()
    private val processedMessages = createProcessedMessages()
    // We add the peer to the key, so other peers cannot attempt malicious meddling with sequence numbers.
    // Expire after 7 days since we last touched an entry, to avoid infinite growth.
    private val senderUUIDSeqNoHWM: MutableMap<SenderKey, SenderHashToSeqNo> = Caffeine.newBuilder().expireAfterAccess(7, TimeUnit.DAYS).build<SenderKey, SenderHashToSeqNo>().asMap()

    private fun createProcessedMessages(): AppendOnlyPersistentMap<DeduplicationId, MessageMeta, ProcessedMessage, String> {
        return AppendOnlyPersistentMap(
                toPersistentEntityKey = { it.toString },
                fromPersistentEntity = { Pair(DeduplicationId(it.id), MessageMeta(it.insertionTime, it.hash, it.seqNo)) },
                toPersistentEntity = { key: DeduplicationId, value: MessageMeta ->
                    ProcessedMessage().apply {
                        id = key.toString
                        insertionTime = value.insertionTime
                        hash = value.senderHash
                        seqNo = value.senderSeqNo
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
        val senderKey = SenderKey(receivedSenderUUID, msg.peer, msg.isSessionInit)
        val (senderHash, existingSeqNoHWM) = senderUUIDSeqNoHWM.computeIfAbsent(senderKey) {
            highestSeqNoHWMInDatabaseFor(senderKey)
        }
        val isNewHWM = (existingSeqNoHWM == null || existingSeqNoHWM < receivedSenderSeqNo)
        return if (isNewHWM) {
            senderUUIDSeqNoHWM[senderKey] = senderHash to receivedSenderSeqNo
            false
        } else isDuplicateInDatabase(msg)
    }

    /**
     * Work out the highest sequence number for the given sender, as persisted last time we ran.
     *
     * TODO: consider the performance of doing this per sender vs. one big load at startup, vs. adding an index (and impact on inserts).
     */
    private fun highestSeqNoHWMInDatabaseFor(senderKey: SenderKey): SenderHashToSeqNo {
        val senderHash = senderHash(senderKey)
        return senderHash to database.transaction {
            val cb1 = session.criteriaBuilder
            val cq1 = cb1.createQuery(Long::class.java)
            val root = cq1.from(ProcessedMessage::class.java)
            session.createQuery(cq1.select(cb1.max(root.get<Long>(ProcessedMessage::seqNo.name))).where(cb1.equal(root.get<String>(ProcessedMessage::hash.name), senderHash))).singleResult
        }
    }

    // We need to incorporate the sending party, and the sessionInit flag as per the in-memory cache.
    private fun senderHash(senderKey: SenderKey) = SecureHash.sha256(senderKey.peer.toString() + senderKey.isSessionInit.toString() + senderKey.senderUUID).toString()

    /**
     * @return true if we have seen this message before.
     */
    fun isDuplicate(msg: ReceivedMessage): Boolean {
        if (beingProcessedMessages.containsKey(msg.uniqueMessageId)) {
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
    fun signalMessageProcessStart(msg: ReceivedMessage) {
        val receivedSenderUUID = msg.senderUUID
        val receivedSenderSeqNo = msg.senderSeqNo
        // We don't want a mix of nulls and values so we ensure that here.
        val senderHash: String? = if (receivedSenderUUID != null && receivedSenderSeqNo != null) senderUUIDSeqNoHWM[SenderKey(receivedSenderUUID, msg.peer, msg.isSessionInit)]?.first else null
        val senderSeqNo: Long? = if (senderHash != null) msg.senderSeqNo else null
        beingProcessedMessages[msg.uniqueMessageId] = MessageMeta(Instant.now(), senderHash, senderSeqNo)
    }

    /**
     * Called inside a DB transaction to persist [deduplicationId].
     */
    fun persistDeduplicationId(deduplicationId: DeduplicationId) {
        processedMessages[deduplicationId] = beingProcessedMessages[deduplicationId]!!
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
            @Column(name = "message_id", length = 64, nullable = false)
            var id: String = "",

            @Column(name = "insertion_time", nullable = false)
            var insertionTime: Instant = Instant.now(),

            @Column(name = "sender", length = 64, nullable = true)
            var hash: String? = "",

            @Column(name = "sequence_number", nullable = true)
            var seqNo: Long? = null
    )

    private data class MessageMeta(val insertionTime: Instant, val senderHash: String?, val senderSeqNo: Long?)

    private data class SenderKey(val senderUUID: String, val peer: CordaX500Name, val isSessionInit: Boolean)
}
