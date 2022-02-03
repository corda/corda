package net.corda.nodeapi.internal.persistence

import net.corda.core.node.ServiceHub
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import javax.persistence.metamodel.Metamodel

/**
 * A delegate of [EntityManager] which disallows some operations.
 */
class RestrictedEntityManager(private val delegate: EntityManager, private val serviceHub: ServiceHub) : EntityManager by delegate {

    override fun getTransaction(): EntityTransaction {
        return RestrictedEntityTransaction(delegate.transaction, serviceHub)
    }

    override fun close() {
        restrictDatabaseOperationFromEntityManager("close", serviceHub) { delegate.close() }
    }

    override fun <T : Any?> unwrap(cls: Class<T>?): T {
        return restrictDatabaseOperationFromEntityManager("unwrap", serviceHub) { delegate.unwrap(cls) }
    }

    override fun getDelegate(): Any {
        return restrictDatabaseOperationFromEntityManager("getDelegate", serviceHub) { delegate.delegate }
    }

    override fun getMetamodel(): Metamodel? {
        return restrictDatabaseOperationFromEntityManager("getMetamodel", serviceHub) { delegate.metamodel }
    }

    override fun joinTransaction() {
        restrictDatabaseOperationFromEntityManager("joinTransaction", serviceHub) { delegate.joinTransaction() }
    }

    override fun lock(entity: Any?, lockMode: LockModeType?) {
        restrictDatabaseOperationFromEntityManager("lock", serviceHub) { delegate.lock(entity, lockMode) }
    }

    override fun lock(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) {
        restrictDatabaseOperationFromEntityManager("lock", serviceHub) { delegate.lock(entity, lockMode, properties) }
    }

    override fun setProperty(propertyName: String?, value: Any?) {
        restrictDatabaseOperationFromEntityManager("lock", serviceHub) { delegate.setProperty(propertyName, value) }
    }
}

class RestrictedEntityTransaction(
    private val delegate: EntityTransaction,
    private val serviceHub: ServiceHub
) : EntityTransaction by delegate {

    override fun rollback() {
        restrictDatabaseOperationFromEntityManager("EntityTransaction.rollback", serviceHub) { delegate.rollback() }
    }

    override fun commit() {
        restrictDatabaseOperationFromEntityManager("EntityTransaction.commit", serviceHub) { delegate.commit() }
    }

    override fun begin() {
        restrictDatabaseOperationFromEntityManager("EntityTransaction.begin", serviceHub) { delegate.begin() }
    }
}