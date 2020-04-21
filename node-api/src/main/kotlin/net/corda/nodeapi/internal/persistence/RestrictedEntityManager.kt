package net.corda.nodeapi.internal.persistence

import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import javax.persistence.metamodel.Metamodel

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

    override fun getMetamodel(): Metamodel? {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun getTransaction(): EntityTransaction? {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun joinTransaction() {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun lock(entity: Any?, lockMode: LockModeType?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun lock(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

    override fun setProperty(propertyName: String?, value: Any?) {
        throw UnsupportedOperationException("This method cannot be called via ServiceHub.withEntityManager.")
    }

}