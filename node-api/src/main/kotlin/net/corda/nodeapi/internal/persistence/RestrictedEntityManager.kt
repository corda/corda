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
        restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.close() }
    }

    override fun <T : Any?> unwrap(cls: Class<T>?): T {
        return restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.unwrap(cls) }
    }

    override fun getDelegate(): Any {
        return restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.delegate }
    }

    override fun getMetamodel(): Metamodel? {
        return restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.metamodel }
    }

    override fun joinTransaction() {
        restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.joinTransaction() }
    }

    override fun lock(entity: Any?, lockMode: LockModeType?) {
        restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.lock(entity, lockMode) }
    }

    override fun lock(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) {
        restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.lock(entity, lockMode, properties) }
    }

    override fun setProperty(propertyName: String?, value: Any?) {
        restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.setProperty(propertyName, value) }
    }
}

class RestrictedEntityTransaction(
    private val delegate: EntityTransaction,
    private val serviceHub: ServiceHub
) : EntityTransaction by delegate {

    override fun rollback() {
        restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.rollback() }
    }

    override fun commit() {
        restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.commit() }
    }

    override fun begin() {
        restrictDatabaseOperationFromEntityManager(serviceHub) { delegate.begin() }
    }
}