package net.corda.node.services.messaging

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.node.services.statemachine.MessageIdentifier
import net.corda.node.services.statemachine.SenderDeduplicationInfo
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

/**
 * Encapsulate the de-duplication logic.
 */
class P2PMessageDeduplicator(cacheFactory: NamedCacheFactory, private val database: CordaPersistence) {
    // A temporary in-memory set of deduplication IDs and associated high water mark details.
    // When we receive a message we don't persist the ID immediately,
    // so we store the ID here in the meantime (until the persisting db tx has committed). This is because Artemis may
    // redeliver messages to the same consumer if they weren't ACKed.
    private val beingProcessedMessages = ConcurrentHashMap<MessageIdentifier, MessageMeta>()
    private val processedMessages = createProcessedMessages(cacheFactory)

    private fun createProcessedMessages(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<Long, MessageMeta, ProcessedMessage, Long> {
        return AppendOnlyPersistentMap(
                cacheFactory = cacheFactory,
                name = "P2PMessageDeduplicator_processedMessages",
                toPersistentEntityKey = { it },
                fromPersistentEntity = { Pair(it.sessionId, MessageMeta(it.insertionTime, it.hash, it.seqNo, it.lastSeqNo)) },
                toPersistentEntity = { key: Long, value: MessageMeta ->
                    ProcessedMessage().apply {
                        sessionId = key
                        insertionTime = value.insertionTime
                        hash = value.senderHash
                        seqNo = value.senderSeqNo
                        lastSeqNo = value.lastSenderSeqNo
                    }
                },
                persistentEntityClass = ProcessedMessage::class.java
        )
    }

    private fun isDuplicateInDatabase(msg: ReceivedMessage): Boolean = database.transaction { msg.uniqueMessageId.sessionIdentifier in processedMessages }

    // We need to incorporate the sending party, and the sessionInit flag as per the in-memory cache.
    private fun senderHash(senderKey: SenderKey) = SecureHash.sha256(senderKey.peer.toString() + senderKey.isSessionInit.toString() + senderKey.senderUUID).toString()

    /**
     * @return true if we have seen this message before.
     */
    fun isDuplicate(msg: ReceivedMessage): Boolean {
        if (beingProcessedMessages.containsKey(msg.uniqueMessageId)) {
            return true
        }
        return isDuplicateInDatabase(msg)
    }

    /**
     * Called the first time we encounter [deduplicationId].
     */
    fun signalMessageProcessStart(msg: ReceivedMessage) {
        val receivedSenderUUID = msg.senderUUID
        val receivedSenderSeqNo = msg.senderSeqNo
        // We don't want a mix of nulls and values so we ensure that here.
        val senderHash: String? = if (receivedSenderUUID != null && receivedSenderSeqNo != null) senderHash(SenderKey(receivedSenderUUID, msg.peer, msg.isSessionInit)) else null
        val senderSeqNo: Long? = if (senderHash != null) msg.senderSeqNo else null
        beingProcessedMessages[msg.uniqueMessageId] = MessageMeta(msg.uniqueMessageId.timestamp, senderHash, senderSeqNo, null)
    }

    /**
     * Called inside a DB transaction to persist [deduplicationId].
     */
    fun persistDeduplicationId(messageId: MessageIdentifier) {
        processedMessages[messageId.sessionIdentifier] = beingProcessedMessages[messageId]!!
    }

    /**
     * Called after the DB transaction persisting [deduplicationId] committed.
     * Any subsequent redelivery will be deduplicated using the DB.
     */
    fun signalMessageProcessFinish(messageId: MessageIdentifier) {
        beingProcessedMessages.remove(messageId)
    }

    /**
     * Called inside a DB transaction to update entry for corresponding session.
     */
    @Suspendable
    fun signalSessionEnd(sessionId: Long, lastSenderDedupInfo: SenderDeduplicationInfo) {
        if (lastSenderDedupInfo.senderSequenceNumber != null && lastSenderDedupInfo.senderUUID != null) {
            val existingEntry = processedMessages[sessionId]
            if (existingEntry != null) {
                val newEntry = existingEntry.copy(lastSenderSeqNo = lastSenderDedupInfo.senderSequenceNumber)
                processedMessages.addOrUpdate(sessionId, newEntry) { k, v ->
                    update(k, v)
                }
            }
        }
    }

    private fun update(key: Long, value: MessageMeta): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(ProcessedMessage::class.java)
        val queryRoot = criteriaUpdate.from(ProcessedMessage::class.java)
        criteriaUpdate.set(ProcessedMessage::lastSeqNo.name, value.lastSenderSeqNo)
        criteriaUpdate.where(criteriaBuilder.equal(queryRoot.get<Long>(ProcessedMessage::sessionId.name), key))
        val update = session.createQuery(criteriaUpdate)
        val rowsUpdated = update.executeUpdate()
        return rowsUpdated != 0
    }

    @Entity
    @Suppress("MagicNumber") // database column width
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}session_data")
    class ProcessedMessage(
            @Id
            @Column(name = "session_id", length = 64, nullable = false)
            var sessionId: Long = 1,

            @Column(name = "insertion_time", nullable = false)
            var insertionTime: Instant = Instant.now(),

            @Column(name = "sender", length = 64, nullable = true)
            var hash: String? = "",

            @Column(name = "sequence_number", nullable = true)
            var seqNo: Long? = null,

            @Column(name = "last_sequence_number", nullable = true)
            var lastSeqNo: Long? = null
    )

    private data class MessageMeta(val insertionTime: Instant, val senderHash: String?, val senderSeqNo: Long?, val lastSenderSeqNo: Long?)

    private data class SenderKey(val senderUUID: String, val peer: CordaX500Name, val isSessionInit: Boolean)
}
