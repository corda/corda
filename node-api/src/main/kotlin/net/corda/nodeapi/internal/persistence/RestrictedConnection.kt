package net.corda.nodeapi.internal.persistence

import net.corda.core.node.ServiceHub
import java.sql.Connection
import java.sql.Savepoint
import java.util.concurrent.Executor

/**
 * A delegate of [Connection] which disallows some operations.
 */
@Suppress("TooManyFunctions")
class RestrictedConnection(private val delegate: Connection, private val serviceHub: ServiceHub) : Connection by delegate {

    override fun abort(executor: Executor?) {
        restrictDatabaseOperationFromJdbcSession("abort", serviceHub) { delegate.abort(executor) }
    }

    override fun clearWarnings() {
        restrictDatabaseOperationFromJdbcSession("clearWarnings", serviceHub) { delegate.clearWarnings() }
    }

    override fun close() {
        restrictDatabaseOperationFromJdbcSession("close", serviceHub) { delegate.close() }
    }

    override fun commit() {
        restrictDatabaseOperationFromJdbcSession("commit", serviceHub) { delegate.commit() }
    }

    override fun setSavepoint(): Savepoint? {
        return restrictDatabaseOperationFromJdbcSession("setSavepoint", serviceHub) { delegate.setSavepoint() }
    }

    override fun setSavepoint(name: String?): Savepoint? {
        return restrictDatabaseOperationFromJdbcSession("setSavepoint", serviceHub) { delegate.setSavepoint(name) }
    }

    override fun releaseSavepoint(savepoint: Savepoint?) {
        restrictDatabaseOperationFromJdbcSession("releaseSavepoint", serviceHub) { delegate.releaseSavepoint(savepoint) }
    }

    override fun rollback() {
        restrictDatabaseOperationFromJdbcSession("rollback", serviceHub) { delegate.rollback() }
    }

    override fun rollback(savepoint: Savepoint?) {
        restrictDatabaseOperationFromJdbcSession("rollback", serviceHub) { delegate.rollback(savepoint) }
    }

    override fun setCatalog(catalog: String?) {
        restrictDatabaseOperationFromJdbcSession("setCatalog", serviceHub) { delegate.catalog = catalog }
    }

    override fun setTransactionIsolation(level: Int) {
        restrictDatabaseOperationFromJdbcSession("setTransactionIsolation", serviceHub) { delegate.transactionIsolation = level }
    }

    override fun setTypeMap(map: MutableMap<String, Class<*>>?) {
        restrictDatabaseOperationFromJdbcSession("setTypeMap", serviceHub) { delegate.typeMap = map }
    }

    override fun setHoldability(holdability: Int) {
        restrictDatabaseOperationFromJdbcSession("setHoldability", serviceHub) { delegate.holdability = holdability }
    }

    override fun setSchema(schema: String?) {
        restrictDatabaseOperationFromJdbcSession("setSchema", serviceHub) { delegate.schema = schema }
    }

    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) {
        restrictDatabaseOperationFromJdbcSession("setNetworkTimeout", serviceHub) { delegate.setNetworkTimeout(executor, milliseconds) }
    }

    override fun setAutoCommit(autoCommit: Boolean) {
        restrictDatabaseOperationFromJdbcSession("setAutoCommit", serviceHub) { delegate.autoCommit = autoCommit }
    }

    override fun setReadOnly(readOnly: Boolean) {
        restrictDatabaseOperationFromJdbcSession("setReadOnly", serviceHub) { delegate.isReadOnly = readOnly }
    }
}