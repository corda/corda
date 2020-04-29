package net.corda.nodeapi.internal.persistence

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Test
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import kotlin.test.assertTrue

class RestrictedEntityManagerTest {
    private val entitymanager = mock<EntityManager>()
    private val transaction = mock<EntityTransaction>()
    private val restrictedEntityManager = RestrictedEntityManager(entitymanager)

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testClose() {
        restrictedEntityManager.close()
    }

    @Test(timeout = 300_000)
    fun testClear() {
        restrictedEntityManager.clear()
    }

    @Test(expected = UnsupportedOperationException::class, timeout=300_000)
    fun testGetMetaModel() {
        restrictedEntityManager.getMetamodel()
    }

    @Test(timeout = 300_000)
    fun testGetTransaction() {
        whenever(entitymanager.transaction).doReturn(transaction)
        assertTrue(restrictedEntityManager.transaction is RestrictedEntityTransaction)
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