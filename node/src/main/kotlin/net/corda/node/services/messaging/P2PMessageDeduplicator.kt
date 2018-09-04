package net.corda.node.services.messaging

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.node.services.statemachine.DeduplicationId
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

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

    private fun createProcessedMessages(): AppendOnlyPersistentMap<DeduplicationId, MessageMeta, ProcessedMessage, String> {
        return AppendOnlyPersistentMap(
                "P2PMessageDeduplicator_processedMessages",
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
