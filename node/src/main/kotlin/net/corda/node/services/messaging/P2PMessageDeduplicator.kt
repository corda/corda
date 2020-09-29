package net.corda.node.services.messaging

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.utilities.contextLogger
import net.corda.node.services.statemachine.MessageType
import net.corda.node.services.statemachine.SessionId
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.math.BigInteger
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * This component is responsible for determining whether session-init messages are duplicates and it also keeps track of information related to
 * sessions that can be used for this purpose.
 */
class P2PMessageDeduplicator(cacheFactory: NamedCacheFactory, private val database: CordaPersistence) {

    companion object {
        private val logger = contextLogger()
    }

    // A temporary in-memory set of deduplication IDs and associated high water mark details.
    // When we receive a message we don't persist the ID immediately,
    // so we store the ID here in the meantime (until the persisting db tx has committed). This is because Artemis may
    // redeliver messages to the same consumer if they weren't ACKed.
    private val beingProcessedMessages = ConcurrentHashMap<MessageIdentifier, MessageMeta>()

    /**
     * This table holds data *only* for sessions that have been initiated from a counterparty (e.g. ones we have received session-init messages from).
     * This is because any other messages apart from session-init messages are deduplicated by the state machine.
     */
    private val sessionData = createSessionDataMap(cacheFactory)

    private fun createSessionDataMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<SessionId, MessageMeta, SessionData, String> {
        return AppendOnlyPersistentMap(
                cacheFactory = cacheFactory,
                name = "P2PMessageDeduplicator_sessionData",
                toPersistentEntityKey = { it.toHex() },
                fromPersistentEntity = { Pair(SessionId.fromHex(it.sessionId), MessageMeta(it.generationTime, it.senderHash, it.firstSenderSeqNo, it.lastSenderSeqNo)) },
                toPersistentEntity = { key: SessionId, value: MessageMeta ->
                    SessionData().apply {
                        sessionId = key.toHex()
                        generationTime = value.generationTime
                        senderHash = value.senderHash
                        firstSenderSeqNo = value.firstSenderSeqNo
                        lastSenderSeqNo = value.lastSenderSeqNo
                    }
                },
                persistentEntityClass = SessionData::class.java
        )
    }

    private fun isDuplicateInDatabase(msg: ReceivedMessage): Boolean = database.transaction { msg.uniqueMessageId.sessionIdentifier in sessionData }

    // We need to incorporate the sending party, and the sessionInit flag as per the in-memory cache.
    private fun senderHash(senderKey: SenderKey) = SecureHash.sha256(senderKey.peer.toString() + senderKey.isSessionInit.toString() + senderKey.senderUUID).toString()

    /**
     * Determines whether a session-init message is a duplicate.
     * This is achieved by checking whether this message is currently being processed or if the associated session has already been created in the past.
     * This method should be invoked only with session-init messages, otherwise it will fail with an [IllegalArgumentException].
     *
     * @return true if we have seen this message before.
     */
    fun isDuplicateSessionInit(msg: ReceivedMessage): Boolean {
        require(msg.uniqueMessageId.messageType == MessageType.SESSION_INIT) { "Message ${msg.uniqueMessageId} was not a session-init message." }

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
        val firstSenderSeqNo: Long? = if (senderHash != null) msg.senderSeqNo else null
        beingProcessedMessages[msg.uniqueMessageId] = MessageMeta(msg.uniqueMessageId.timestamp, senderHash, firstSenderSeqNo, null)
    }

    /**
     * Called inside a DB transaction to persist [deduplicationId].
     */
    fun persistDeduplicationId(deduplicationId: MessageIdentifier) {
        sessionData[deduplicationId.sessionIdentifier] = beingProcessedMessages[deduplicationId]!!
    }

    /**
     * Called after the DB transaction persisting [deduplicationId] committed.
     * Any subsequent redelivery will be deduplicated using the DB.
     */
    fun signalMessageProcessFinish(deduplicationId: MessageIdentifier) {
        beingProcessedMessages.remove(deduplicationId)
    }

    /**
     * Called inside a DB transaction to update entry for corresponding session.
     * The parameters [senderUUID] and [senderSequenceNumber] correspond to the last message seen from this session before it ended.
     * If [senderUUID] is not null, then [senderSequenceNumber] is also expected to not be null.
     */
    @Suspendable
    fun signalSessionEnd(sessionId: SessionId, senderUUID: String?, senderSequenceNumber: Long?) {
        if (senderSequenceNumber != null && senderUUID != null) {
            val existingEntry = sessionData[sessionId]
            if (existingEntry != null) {
                val newEntry = existingEntry.copy(lastSenderSeqNo = senderSequenceNumber)
                sessionData.addOrUpdate(sessionId, newEntry) { k, v ->
                    update(k, v)
                }
            }
        }
    }

    private fun update(key: SessionId, value: MessageMeta): Boolean {
        val session = currentDBSession()
        val criteriaBuilder = session.criteriaBuilder
        val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(SessionData::class.java)
        val queryRoot = criteriaUpdate.from(SessionData::class.java)
        criteriaUpdate.set(SessionData::lastSenderSeqNo.name, value.lastSenderSeqNo)
        criteriaUpdate.where(criteriaBuilder.equal(queryRoot.get<BigInteger>(SessionData::sessionId.name), key.toHex()))
        val update = session.createQuery(criteriaUpdate)
        val rowsUpdated = update.executeUpdate()
        return rowsUpdated != 0
    }

    @Entity
    @Suppress("MagicNumber") // database column width
    @Table(name = "${NODE_DATABASE_PREFIX}session_data")
    class SessionData (
            /**
             * The session identifier in hexadecimal form.
             */
            @Id
            @Column(name = "session_id", nullable = false)
            var sessionId: String = "",

            /**
             * The time the corresponding session-init message was originally generated on the sender side.
             */
            @Column(name = "init_generation_time", nullable = false)
            var generationTime: Instant = Instant.now(),

            @Column(name = "sender_hash", length = 64, nullable = true)
            var senderHash: String? = "",

            /**
             * The sender sequence number of the first message seen in a session.
             */
            @Column(name = "init_sequence_number", nullable = true)
            var firstSenderSeqNo: Long? = null,

            /**
             * The sender sequence number of the last message seen in a session before it was closed/terminated.
             */
            @Column(name = "last_sequence_number", nullable = true)
            var lastSenderSeqNo: Long? = null
    )

    private data class MessageMeta(val generationTime: Instant, val senderHash: String?, val firstSenderSeqNo: SenderSequenceNumber?, val lastSenderSeqNo: SenderSequenceNumber?)

    private data class SenderKey(val senderUUID: String, val peer: CordaX500Name, val isSessionInit: Boolean)
}
