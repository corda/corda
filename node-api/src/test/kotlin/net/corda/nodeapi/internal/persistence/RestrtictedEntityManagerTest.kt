package net.corda.nodeapi.internal.persistence

import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import javax.persistence.EntityManager
import javax.persistence.LockModeType

class RestrtictedEntityManagerTest {
    private val entitymanager = mock<EntityManager>()
    private val restrictedEntityManager = RestrictedEntityManager(entitymanager)

    @Test(expected = UnsupportedOperationException::class)
    fun testClose(){
        restrictedEntityManager.close()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testClear(){
        restrictedEntityManager.clear()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testGetMetaModel(){
        restrictedEntityManager.getMetamodel()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testGetTransaction(){
        restrictedEntityManager.getTransaction()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testJoinTransaction(){
        restrictedEntityManager.joinTransaction()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testLockWithTwoParameters(){
        restrictedEntityManager.lock(Object(),LockModeType.OPTIMISTIC)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testLockWithThreeParameters(){
        val map: MutableMap<String,Any> = mutableMapOf()
        restrictedEntityManager.lock(Object(),LockModeType.OPTIMISTIC,map)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetProperty(){
        restrictedEntityManager.setProperty("number",12)
    }
}