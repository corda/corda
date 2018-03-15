/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import com.codahale.metrics.MetricRegistry
import com.google.common.base.Stopwatch
import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryInternalException
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.Party
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.MySQLConfiguration
import java.sql.BatchUpdateException
import java.sql.Connection
import java.sql.SQLTransientConnectionException
import java.util.concurrent.TimeUnit

/**
 * Uniqueness provider backed by a MySQL database. It is intended to be used with a multi-master synchronously replicated
 * variant of MySQL, such as Percona XtraDB Cluster, or MariaDB Galera Cluster.
 *
 * Note that no ORM is used since we want to retain full control over table schema and be able to experiment with optimisations.
 */
class MySQLUniquenessProvider(
        metrics: MetricRegistry,
        configuration: MySQLConfiguration
) : UniquenessProvider, SingletonSerializeAsToken() {

    companion object {
        private val log = loggerFor<MySQLUniquenessProvider>()

        // TODO: optimize table schema for InnoDB
        private val createCommittedStateTable =
                "CREATE TABLE IF NOT EXISTS notary_committed_states (" +
                        "issue_transaction_id BINARY(32) NOT NULL," +
                        "issue_transaction_output_id INT UNSIGNED NOT NULL," +
                        "consuming_transaction_id BINARY(32) NOT NULL," +
                        "CONSTRAINT id PRIMARY KEY (issue_transaction_id, issue_transaction_output_id)" +
                        ")"
        private val insertStateStatement = "INSERT INTO notary_committed_states (issue_transaction_id, issue_transaction_output_id, consuming_transaction_id) VALUES (?, ?, ?)"
        private val findStatement = "SELECT consuming_transaction_id FROM notary_committed_states WHERE issue_transaction_id = ? AND issue_transaction_output_id = ?"

        private val createRequestLogTable =
                "CREATE TABLE IF NOT EXISTS notary_request_log (" +
                        "consuming_transaction_id BINARY(32) NOT NULL," +
                        "requesting_party_name TEXT NOT NULL," +
                        "request_signature BLOB NOT NULL," +
                        "request_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "request_id INT UNSIGNED NOT NULL AUTO_INCREMENT," +
                        "CONSTRAINT rid PRIMARY KEY (request_id)" +
                        ")"
        private val insertRequestStatement = "INSERT INTO notary_request_log (consuming_transaction_id, requesting_party_name, request_signature) VALUES (?, ?, ?)"
    }

    private val metricPrefix = MySQLUniquenessProvider::class.simpleName
    /** Transaction commit duration and rate metric timer */
    private val commitTimer = metrics.timer("$metricPrefix.Commit")
    /**
     * When writing to multiple masters with Galera, transaction rollbacks may happen due to high write contention.
     * This is a useful heath metric.
     */
    private val rollbackCounter = metrics.counter("$metricPrefix.Rollback")
    /** Incremented when we can not obtain a DB connection. */
    private val connectionExceptionCounter = metrics.counter("$metricPrefix.ConnectionException")
    /** Track double spend attempts. Note that this will also include notarisation retries. */
    private val conflictCounter = metrics.counter("$metricPrefix.Conflicts")
    /** Track the distribution of the number of input states **/
    private val nrInputStates = metrics.histogram("$metricPrefix.NumberOfInputStates")

    val dataSource = HikariDataSource(HikariConfig(configuration.dataSource))
    private val connectionRetries = configuration.connectionRetries

    private val connection: Connection
        get() = getConnection()

    private fun getConnection(nRetries: Int = 0): Connection =
            try {
                dataSource.connection
            } catch (e: SQLTransientConnectionException) {
                if (nRetries == connectionRetries) {
                    log.warn("Couldn't obtain connection with {} retries, giving up, {}", nRetries, e)
                    throw e
                }
                log.warn("Connection exception, retrying", nRetries+1)
                connectionExceptionCounter.inc()
                getConnection(nRetries + 1)
            }

    fun createTable() {
        log.debug("Attempting to create DB table if it does not yet exist: $createCommittedStateTable")
        connection.use {
            it.createStatement().execute(createCommittedStateTable)
            it.createStatement().execute(createRequestLogTable)
            it.commit()
        }
    }

    fun stop() {
        dataSource.close()
    }

    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party, requestSignature: NotarisationRequestSignature) {
        val s = Stopwatch.createStarted()
        try {
            retryTransaction(CommitAll(states, txId, callerIdentity, requestSignature))
            nrInputStates.update(states.size)
        } catch (e: BatchUpdateException) {
            log.info("Unable to commit input states, finding conflicts, txId: $txId", e)
            conflictCounter.inc()
            retryTransaction(FindConflicts(txId, states))
        } finally {
            val dt = s.stop().elapsed(TimeUnit.MILLISECONDS)
            commitTimer.update(dt, TimeUnit.MILLISECONDS)
            log.info("Processed notarisation request, txId: $txId, nrInputStates: ${states.size}, dt: $dt")
        }
    }

    private fun retryTransaction(tx: RetryableTransaction) {
        connection.use {
            while (true) {
                try {
                    tx.run(it)
                } catch (e: Exception) {
                    if (e is MySQLTransactionRollbackException) {
                        log.warn("Rollback exception occurred, retrying", e)
                        rollbackCounter.inc()
                        continue
                    } else {
                        log.warn("Attempting to rollback", e)
                        it.rollback()
                        throw e
                    }
                }
                break
            }
            it.commit()
        }
    }

    interface RetryableTransaction {
        fun run(conn: Connection)
    }

    private class CommitAll(val states: List<StateRef>, val txId: SecureHash, val callerIdentity: Party, val requestSignature: NotarisationRequestSignature) : RetryableTransaction {
        override fun run(conn: Connection) {
            conn.prepareStatement(insertRequestStatement).apply {
                setBytes(1, txId.bytes)
                setString(2, callerIdentity.name.toString())
                setBytes(3, requestSignature.serialize().bytes)
                execute()
                close()
            }
            // We commit here, since we want to make sure it doesn't get rolled back in case of a conflict
            // when committing inputs
            conn.commit()
            conn.prepareStatement(insertStateStatement).apply {
                states.forEach { stateRef ->
                    // StateRef
                    setBytes(1, stateRef.txhash.bytes)
                    setInt(2, stateRef.index)
                    // Consuming transaction
                    setBytes(3, txId.bytes)
                    addBatch()
                    clearParameters()
                }
                executeBatch()
                close()
            }
        }
    }

    private class FindConflicts(val txId: SecureHash, val states: List<StateRef>) : RetryableTransaction {
        override fun run(conn: Connection) {
            val conflicts = mutableMapOf<StateRef, StateConsumptionDetails>()
            states.forEach {
                val st = conn.prepareStatement(findStatement).apply {
                    setBytes(1, it.txhash.bytes)
                    setInt(2, it.index)
                }
                val result = st.executeQuery()
                if (result.next()) {
                    val consumingTxId = SecureHash.SHA256(result.getBytes(1))
                    conflicts[it] = StateConsumptionDetails(consumingTxId.sha256())
                }
            }
            conn.commit()
            if (conflicts.isNotEmpty()) throw NotaryInternalException(NotaryError.Conflict(txId, conflicts))
        }
    }
}