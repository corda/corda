package net.corda.node.services.messaging

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.services.statemachine.DeduplicationId
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

typealias SenderHashToSeqNo = Pair<String, Long?>

/**
 * Encapsulate the de-duplication logic.
 *
 * The optimized codepath (in CE) is a bit more complicated. In the simpler codepath we always went through the database when we check whether
 * the message is a duplicate, which is a very expensive operation. The enterprise implementation of the this class uses high
 * watermarking to optimize this away in the happy path.
 *
 * The idea is this: messages contain an incrementing sequence number that's monotonically increasing per *sender identity*. We keep around the
 * highest such number for each sender, and if we see a message coming from the same identity but with a higher sequence number then we can
 * skip the database roundtrip, we know it's a fresh message. In any other case we fallback to the old slower codepath. In practice this is
 * completely sufficient to eliminate almost all read roundtrips to the database on the receive, aside from an initial one.
 */
class P2PMessageDeduplicator(
        cacheFactory: NamedCacheFactory,
        private val database: CordaPersistence,
        private val clock: Clock
) {
    companion object {
        private val log = loggerFor<P2PMessageDeduplicator>()

        private fun formatMessageForLogging(msg: ReceivedMessage): String = "${msg.uniqueMessageId} sender=${msg.senderUUID} senderSequenceNumber=${msg.senderSeqNo} msg=$msg"
        private fun formatMetaForLogging(deduplicationId: DeduplicationId, messageMeta: MessageMeta?): String = "$deduplicationId senderHash=${messageMeta?.senderHash} senderSequenceNumber=${messageMeta?.senderSeqNo}"
    }

    // A temporary in-memory set of deduplication IDs and associated high water mark details.
    // When we receive a message we don't persist the ID immediately,
    // so we store the ID here in the meantime (until the persisting db tx has committed). This is because Artemis may
    // redeliver messages to the same consumer if they weren't ACKed.
    private val beingProcessedMessages = ConcurrentHashMap<DeduplicationId, MessageMeta>()
    private val processedMessages = createProcessedMessages(cacheFactory)
    // We add the peer to the key, so other peers cannot attempt malicious meddling with sequence numbers.
    // Expire after 7 days since we last touched an entry, to avoid infinite growth.
    private val senderUUIDSeqNoHWM: MutableMap<SenderKey, SenderHashToSeqNo> = cacheFactory.buildNamed<SenderKey, SenderHashToSeqNo>(
            Caffeine.newBuilder(),
            "P2PMessageDeduplicator_senderUUIDSeqNoHWM"
    ).asMap()

    private fun createProcessedMessages(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<DeduplicationId, MessageMeta, ProcessedMessage, String> {
        return AppendOnlyPersistentMap(
                cacheFactory = cacheFactory,
                name = "P2PMessageDeduplicator_processedMessages",
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

    private fun isDuplicateInDatabase(msg: ReceivedMessage): Boolean = database.transaction {
        val inDb = msg.uniqueMessageId in processedMessages
        log.trace { "${formatMessageForLogging(msg)} ${if (inDb) "is" else "is NOT"} in the database." }
        inDb
    }

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
            val senderHashToSeqNo = highestSeqNoHWMInDatabaseFor(senderKey)
            log.trace { "${formatMessageForLogging(msg)} senderHash=${senderHashToSeqNo.first} fetched highest sequence number from database of ${senderHashToSeqNo.second}" }
            senderHashToSeqNo
        }
        log.trace { "${formatMessageForLogging(msg)} senderHash=$senderHash high water mark is $existingSeqNoHWM" }
        val isNewHWM = (existingSeqNoHWM == null || existingSeqNoHWM < receivedSenderSeqNo)
        return if (isNewHWM) {
            if (existingSeqNoHWM == null && isDuplicateInDatabase(msg)) {
                log.debug { "${formatMessageForLogging(msg)} is a duplicate in the database but senderHash=$senderHash differs from the original. The identity changed." }
                true
            } else {
                log.trace { "${formatMessageForLogging(msg)} senderHash=$senderHash is new high water mark vs. $existingSeqNoHWM" }
                senderUUIDSeqNoHWM[senderKey] = senderHash to receivedSenderSeqNo
                false
            }
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
            session.createQuery(cq1
                    .select(
                            cb1.max(root.get<Long>(ProcessedMessage::seqNo.name))
                    ).where(
                            cb1.equal(root.get<String>(ProcessedMessage::hash.name), senderHash)
                    )
            ).singleResult
        }
    }

    // We need to incorporate the sending party, and the sessionInit flag as per the in-memory cache.
    private fun senderHash(senderKey: SenderKey) = SecureHash.sha256(senderKey.peer.toString() + senderKey.isSessionInit.toString() + senderKey.senderUUID).toString()

    /**
     * @return true if we have seen this message before.
     */
    fun isDuplicate(msg: ReceivedMessage): Boolean {
        if (beingProcessedMessages.containsKey(msg.uniqueMessageId)) {
            log.trace { "${formatMessageForLogging(msg)} is currently being processed." }
            return true
        }
        log.trace { "${formatMessageForLogging(msg)} is NOT currently being processed." }
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
        val senderHash: String? = if (receivedSenderUUID != null && receivedSenderSeqNo != null) {
            val senderKey = SenderKey(receivedSenderUUID, msg.peer, msg.isSessionInit)
            senderUUIDSeqNoHWM[senderKey]?.first
        } else {
            null
        }
        val senderSeqNo: Long? = if (senderHash != null) msg.senderSeqNo else null
        val messageMeta = MessageMeta(clock.instant(), senderHash, senderSeqNo)
        beingProcessedMessages[msg.uniqueMessageId] = messageMeta
        log.debug { "${formatMetaForLogging(msg.uniqueMessageId, messageMeta)} will be processed." }
    }

    /**
     * Called inside a DB transaction to persist [deduplicationId].
     */
    fun persistDeduplicationId(deduplicationId: DeduplicationId) {
        val messageMeta = beingProcessedMessages[deduplicationId]!!
        log.trace { "${formatMetaForLogging(deduplicationId, messageMeta)} persisted to database." }
        processedMessages[deduplicationId] = messageMeta
    }

    /**
     * Called after the DB transaction persisting [deduplicationId] committed.
     * Any subsequent redelivery will be deduplicated using the DB.
     */
    fun signalMessageProcessFinish(deduplicationId: DeduplicationId) {
        val messageMeta = beingProcessedMessages.remove(deduplicationId)
        log.debug { "${formatMetaForLogging(deduplicationId, messageMeta)} is no longer being processed." }
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
