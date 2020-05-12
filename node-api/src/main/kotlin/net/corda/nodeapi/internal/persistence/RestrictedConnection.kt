package net.corda.nodeapi.internal.persistence

import java.sql.Connection
import java.sql.Savepoint
import java.util.concurrent.Executor

/**
 * A delegate of [Connection] which disallows some operations.
 */
@Suppress("TooManyFunctions")
class RestrictedConnection(private val delegate : Connection) : Connection by delegate {

    override fun abort(executor: Executor?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun clearWarnings() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun close() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun commit() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setSavepoint(): Savepoint? {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setSavepoint(name : String?): Savepoint? {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun releaseSavepoint(savepoint: Savepoint?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun rollback() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun rollback(savepoint: Savepoint?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setCatalog(catalog : String?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setTransactionIsolation(level: Int) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setTypeMap(map: MutableMap<String, Class<*>>?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setHoldability(holdability: Int) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setSchema(schema: String?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setAutoCommit(autoCommit: Boolean) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }

    override fun setReadOnly(readOnly: Boolean) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.jdbcSession.")
    }
}