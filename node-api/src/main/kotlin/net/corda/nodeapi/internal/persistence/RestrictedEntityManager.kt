package net.corda.nodeapi.internal.persistence

import javax.persistence.EntityManager
import javax.persistence.EntityTransaction

/**
 * A delegate of [EntityManager] which disallows some operations.
 */
class RestrictedEntityManager(private val delegate: EntityManager) : EntityManager by delegate {

    override fun close() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun clear() {
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

    override fun getTransaction(): EntityTransaction {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    private fun checkSessionIsNotRolledBack() {
        if (delegate.transaction.rollbackOnly) {
            throw RolledBackDatabaseSessionException()
        }
    }

    // TODO: Figure out which other methods on EntityManager need to be blocked?
}