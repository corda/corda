package net.corda.nodeapi.internal.persistence

import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

/**
 * A delegate of [EntityManager] which disallows some operations.
 */
@Suppress("TooManyFunctions")
class RestrictedEntityManager(private val delegate: EntityManager) : EntityManager by delegate {

    override fun close() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun getTransaction(): EntityTransaction {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun <T : Any?> unwrap(cls: Class<T>?): T {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun getDelegate(): Any {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun setProperty(propertyName: String?, value: Any?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun persist(entity: Any?) {
        checkSessionIsNotRolledBack()
        delegate.persist(entity)
    }

    override fun <T : Any?> merge(entity: T): T {
        checkSessionIsNotRolledBack()
        return delegate.merge(entity)
    }

    override fun remove(entity: Any?) {
        checkSessionIsNotRolledBack()
        delegate.remove(entity)
    }

    override fun lock(entity: Any?, lockMode: LockModeType?) {
        checkSessionIsNotRolledBack()
        delegate.lock(entity, lockMode)
    }

    override fun lock(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) {
        checkSessionIsNotRolledBack()
        delegate.lock(entity, lockMode, properties)
    }

    override fun refresh(entity: Any?, properties: MutableMap<String, Any>?) {
        checkSessionIsNotRolledBack()
        delegate.refresh(entity, properties)
    }

    override fun refresh(entity: Any?, lockMode: LockModeType?) {
        checkSessionIsNotRolledBack()
        delegate.refresh(entity, lockMode)
    }

    override fun refresh(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) {
        checkSessionIsNotRolledBack()
        delegate.refresh(entity, lockMode, properties)
    }

    override fun refresh(entity: Any?) {
        checkSessionIsNotRolledBack()
        delegate.refresh(entity)
    }

    override fun <T : Any?> find(entityClass: Class<T>?, primaryKey: Any?, properties: MutableMap<String, Any>?): T {
        checkSessionIsNotRolledBack()
        return delegate.find(entityClass, primaryKey, properties)
    }

    override fun <T : Any?> find(entityClass: Class<T>?, primaryKey: Any?, lockMode: LockModeType?): T {
        checkSessionIsNotRolledBack()
        return delegate.find(entityClass, primaryKey, lockMode)
    }

    override fun <T : Any?> find(entityClass: Class<T>?, primaryKey: Any?): T {
        checkSessionIsNotRolledBack()
        return delegate.find(entityClass, primaryKey)
    }

    override fun <T : Any?> find(
        entityClass: Class<T>?,
        primaryKey: Any?,
        lockMode: LockModeType?,
        properties: MutableMap<String, Any>?
    ): T {
        checkSessionIsNotRolledBack()
        return delegate.find(entityClass, primaryKey, lockMode, properties)
    }

    private fun checkSessionIsNotRolledBack() {
        if (delegate.transaction.rollbackOnly) {
            throw RolledBackDatabaseSessionException()
        }
    }

    // TODO: Figure out which other methods on EntityManager need to be blocked?
}