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
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.abort(executor) }
    }

    override fun clearWarnings() {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.clearWarnings() }
    }

    override fun close() {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.close() }
    }

    override fun commit() {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.commit() }
    }

    override fun setSavepoint(): Savepoint? {
        return restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.setSavepoint() }
    }

    override fun setSavepoint(name: String?): Savepoint? {
        return restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.setSavepoint(name) }
    }

    override fun releaseSavepoint(savepoint: Savepoint?) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.releaseSavepoint(savepoint) }
    }

    override fun rollback() {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.rollback() }
    }

    override fun rollback(savepoint: Savepoint?) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.rollback(savepoint) }
    }

    override fun setCatalog(catalog: String?) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.catalog = catalog }
    }

    override fun setTransactionIsolation(level: Int) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.transactionIsolation = level }
    }

    override fun setTypeMap(map: MutableMap<String, Class<*>>?) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.typeMap = map }
    }

    override fun setHoldability(holdability: Int) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.holdability = holdability }
    }

    override fun setSchema(schema: String?) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.schema = schema }
    }

    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.setNetworkTimeout(executor, milliseconds) }
    }

    override fun setAutoCommit(autoCommit: Boolean) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.autoCommit = autoCommit }
    }

    override fun setReadOnly(readOnly: Boolean) {
        restrictDatabaseOperationFromJdbcSession(serviceHub) { delegate.isReadOnly = readOnly }
    }
}