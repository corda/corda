package net.corda.nodeapi.internal.persistence

import javax.persistence.EntityManager
import javax.persistence.EntityTransaction

/**
 * A delegate of [EntityManager] which disallows some operations.
 */
@Suppress("TooManyFunctions")
class RestrictedEntityManager(private val delegate: EntityManager) : EntityManager by delegate {

    override fun getTransaction(): EntityTransaction {
        return RestrictedEntityTransaction(delegate.transaction)
    }

    override fun close() {
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
}

class RestrictedEntityTransaction(private val delegate: EntityTransaction) : EntityTransaction by delegate {

    override fun rollback() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun commit() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun begin() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }
}