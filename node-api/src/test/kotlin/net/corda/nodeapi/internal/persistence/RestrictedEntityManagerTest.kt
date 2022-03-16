package net.corda.nodeapi.internal.persistence

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.node.ServiceHub
import org.junit.Test
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import kotlin.test.assertTrue

class RestrictedEntityManagerTest {
    private val entitymanager = mock<EntityManager>()
    private val transaction = mock<EntityTransaction>()
    private val cordapp = mock<Cordapp>()
    private val cordappContext = CordappContext.create(cordapp, null, javaClass.classLoader, mock())
    private val serviceHub = mock<ServiceHub>().apply {
        whenever(getAppContext()).thenReturn(cordappContext)
    }
    private val restrictedEntityManager = RestrictedEntityManager(entitymanager, serviceHub)

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `close with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedEntityManager.close()
    }

    @Test(timeout = 300_000)
    fun `clear with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedEntityManager.clear()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `getMetaModel with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedEntityManager.metamodel
    }

    @Test(timeout = 300_000)
    fun `getTransaction with target platform version of current corda version executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        whenever(entitymanager.transaction).doReturn(transaction)
        assertTrue(restrictedEntityManager.transaction is RestrictedEntityTransaction)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `joinTransaction with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedEntityManager.joinTransaction()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `lock with two parameters with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedEntityManager.lock(Object(), LockModeType.OPTIMISTIC)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `lock with three parameters with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        val map: MutableMap<String, Any> = mutableMapOf()
        restrictedEntityManager.lock(Object(), LockModeType.OPTIMISTIC, map)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setProperty with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedEntityManager.setProperty("number", 12)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `close with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedEntityManager.close()
    }

    @Test(timeout = 300_000)
    fun `clear with target platform version of 7 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedEntityManager.clear()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `getMetaModel with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedEntityManager.metamodel
    }

    @Test(timeout = 300_000)
    fun `getTransaction with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        whenever(entitymanager.transaction).doReturn(transaction)
        assertTrue(restrictedEntityManager.transaction is RestrictedEntityTransaction)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `joinTransaction with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedEntityManager.joinTransaction()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `lock with two parameters with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedEntityManager.lock(Object(), LockModeType.OPTIMISTIC)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `lock with three parameters with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        val map: MutableMap<String, Any> = mutableMapOf()
        restrictedEntityManager.lock(Object(), LockModeType.OPTIMISTIC, map)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setProperty with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedEntityManager.setProperty("number", 12)
    }

    @Test(timeout = 300_000)
    fun `close with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedEntityManager.close()
    }

    @Test(timeout = 300_000)
    fun `clear with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedEntityManager.clear()
    }

    @Test(timeout = 300_000)
    fun `getMetaModel with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedEntityManager.metamodel
    }

    @Test(timeout = 300_000)
    fun `getTransaction with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        whenever(entitymanager.transaction).doReturn(transaction)
        assertTrue(restrictedEntityManager.transaction is RestrictedEntityTransaction)
    }

    @Test(timeout = 300_000)
    fun `joinTransaction with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedEntityManager.joinTransaction()
    }

    @Test(timeout = 300_000)
    fun `lock with two parameters with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedEntityManager.lock(Object(), LockModeType.OPTIMISTIC)
    }

    @Test(timeout = 300_000)
    fun `lock with three parameters with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        val map: MutableMap<String, Any> = mutableMapOf()
        restrictedEntityManager.lock(Object(), LockModeType.OPTIMISTIC, map)
    }

    @Test(timeout = 300_000)
    fun `setProperty with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedEntityManager.setProperty("number", 12)
    }
}