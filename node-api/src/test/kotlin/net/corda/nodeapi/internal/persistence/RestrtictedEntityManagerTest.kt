package net.corda.nodeapi.internal.persistence

import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import javax.persistence.EntityManager
import javax.persistence.LockModeType

class RestrtictedEntityManagerTest {
    private val entitymanager = mock<EntityManager>()
    private val restrictedEntityManager = RestrictedEntityManager(entitymanager)

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testClose() {
        restrictedEntityManager.close()
    }

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testClear() {
        restrictedEntityManager.clear()
    }

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testGetMetaModel() {
        restrictedEntityManager.getMetamodel()
    }

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testGetTransaction() {
        restrictedEntityManager.getTransaction()
    }

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testJoinTransaction() {
        restrictedEntityManager.joinTransaction()
    }

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testLockWithTwoParameters() {
        restrictedEntityManager.lock(Object(), LockModeType.OPTIMISTIC)
    }

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testLockWithThreeParameters() {
        val map: MutableMap<String,Any> = mutableMapOf()
        restrictedEntityManager.lock(Object(), LockModeType.OPTIMISTIC,map)
    }

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testSetProperty() {
        restrictedEntityManager.setProperty("number", 12)
    }
}