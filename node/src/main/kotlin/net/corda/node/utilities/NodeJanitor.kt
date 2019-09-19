package net.corda.node.utilities

import net.corda.core.internal.elapsedTime
import net.corda.core.utilities.days
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.trace
import net.corda.node.services.config.ProcessedMessageCleanup
import net.corda.node.services.messaging.P2PMessageDeduplicator
import net.corda.nodeapi.internal.persistence.CordaPersistence
import org.hibernate.Session
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.stream.Stream

object NodeJanitor {
    private val log = loggerFor<NodeJanitor>()
    const val maxRetainForDays = 365 // One year

    /** Removes old processed message records from the database, maintained by the [P2PMessageDeduplicator]. */
    fun cleanUpProcessedMessages(database: CordaPersistence, clock: Clock, retainForDays: Int, retainPerSender: Int) {
        if (retainForDays < 1 || retainForDays > maxRetainForDays || retainPerSender < 1) {
            log.error("Unable to perform processed message id table cleanup, incorrect configuration values specified: " +
                    "${ProcessedMessageCleanup::retainForDays.name} must be within the range of [1, $maxRetainForDays] and ${ProcessedMessageCleanup::retainPerSender.name} must be positive. " +
                    "Given: ${ProcessedMessageCleanup::retainForDays.name} = $retainForDays, " +
                    "${ProcessedMessageCleanup::retainPerSender.name} = $retainPerSender")
            return
        }

        log.info("Starting processed message id table cleanup. Removing records older than $retainForDays days, keeping only $retainPerSender records per unique sender")
        var removed = 0
        var totalElapsedTime = Duration.ZERO

        try {
            database.transaction {
                totalElapsedTime += elapsedTime {
                    // Step 1: Remove records older than X days
                    removed += removeOldRecords(session, clock, retainForDays)
                }
                log.info("Removed $removed records older than $retainForDays days, duration: $totalElapsedTime")

                totalElapsedTime += elapsedTime {
                    // Step 2: For every sender hash, retain only Y latest records
                    removed += leaveOnlyLatestPerSender(session, retainPerSender)
                }
            }
            log.info("Finished cleaning up processed message id table, total records removed: $removed, total duration: $totalElapsedTime")
        } catch (e: Exception) {
            log.warn("Unable to perform deduplicated message cleanup", e)
        }
    }

    /** Removes all processed messages older than the specified time. */
    private fun removeOldRecords(session: Session, clock: Clock, retainForDays: Int): Int {
        val builder = session.criteriaBuilder

        val delete = builder.createCriteriaDelete(P2PMessageDeduplicator.ProcessedMessage::class.java)
        val root = delete.from(P2PMessageDeduplicator.ProcessedMessage::class.java)
        val insertionTime = root.get<Instant>(P2PMessageDeduplicator.ProcessedMessage::insertionTime.name)
        val keepUntil: Instant = clock.instant() - retainForDays.days

        delete.where(
                builder.lessThan(insertionTime, keepUntil)
        )
        return session.createQuery(delete).executeUpdate()
    }

    /** Keeps only N most recently processed messages per sender in the database. */
    private fun leaveOnlyLatestPerSender(session: Session, retainPerSender: Int): Int {
        val tableName = P2PMessageDeduplicator.ProcessedMessage::class.java.name
        val senderCol = P2PMessageDeduplicator.ProcessedMessage::hash.name
        val seqNoCol = P2PMessageDeduplicator.ProcessedMessage::seqNo.name

        /** Retrieve sender hashes for which there are more than [retainPerSender] processed messages recorded. */
        fun findSendersForCleanup(): Stream<Pair<String, Long>> {
            val queryString = "SELECT m.$senderCol, COUNT(m) " +
                    "FROM $tableName m " +
                    "WHERE m.$senderCol is not null AND m.$seqNoCol is not null " +
                    "GROUP BY m.$senderCol " +
                    "HAVING COUNT(m) > :retainPerSender"
            val query = session
                    .createQuery(queryString)
                    .setParameter("retainPerSender", retainPerSender.toLong())

            return query.resultStream.map {
                val resultRow = it as Array<*>
                val senderHash = resultRow[0] as String
                val messageCount = resultRow[1] as Long
                senderHash to messageCount
            }
        }

        /** Find the cutoff sequence number below which (and including) message records should be removed. */
        fun findCutoffSeqNo(senderHash: String): Long {
            val queryString = "SELECT $seqNoCol " +
                    "FROM $tableName " +
                    "WHERE $senderCol = :senderHash " +
                    "ORDER BY $seqNoCol DESC"
            val query = session.createQuery(queryString)
                    .setParameter("senderHash", senderHash)
                    .setFirstResult(retainPerSender)
                    .setMaxResults(1)


            return query.singleResult as Long
        }

        var totalRemoved = 0
        findSendersForCleanup().forEach { (senderHash, _) ->
            val cutoffSeqNo = findCutoffSeqNo(senderHash)
            log.trace { "Lowest message sequence number for sender $senderHash to keep: $cutoffSeqNo" }

            val queryString = "DELETE FROM $tableName " +
                    "WHERE $senderCol = :senderHash " +
                    "AND $seqNoCol <= :cutoffSeqNo"

            val removedRecords = session
                    .createQuery(queryString)
                    .setParameter("senderHash", senderHash)
                    .setParameter("cutoffSeqNo", cutoffSeqNo)
                    .executeUpdate()

            if (removedRecords > 0) {
                log.info("Removed $removedRecords records for sender $senderHash")
            }
            totalRemoved += removedRecords
        }
        return totalRemoved
    }
}