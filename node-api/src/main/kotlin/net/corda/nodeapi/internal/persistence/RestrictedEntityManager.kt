package net.corda.nodeapi.internal.persistence

import javax.persistence.EntityManager

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

    // TODO: Figure out which other methods on EntityManager need to be blocked?
}