package net.corda.nodeapi.internal.persistence

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.node.ServiceHub
import org.junit.Test
import java.sql.Connection
import java.sql.Savepoint

class RestrictedConnectionTest {

    private val connection: Connection = mock()
    private val savePoint: Savepoint = mock()
    private val cordapp = mock<Cordapp>()
    private val cordappContext = CordappContext.create(cordapp, null, javaClass.classLoader, mock())
    private val serviceHub = mock<ServiceHub>().apply {
        whenever(getAppContext()).thenReturn(cordappContext)
    }
    private val restrictedConnection: RestrictedConnection = RestrictedConnection(connection, serviceHub)

    companion object {
        private const val TEST_STRING: String = "test"
        private const val TEST_INT: Int = 1
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `abort with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.abort { println("I'm just an executor for this test...") }
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `clearWarnings with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.clearWarnings()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `close with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.close()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `commit with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.commit()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setSavepoint with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.setSavepoint()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setSavepoint with name with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.setSavepoint(TEST_STRING)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `releaseSavepoint with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.releaseSavepoint(savePoint)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `rollback with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.rollback()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `rollbackWithSavepoint with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.rollback(savePoint)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setCatalog with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.catalog = TEST_STRING
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setTransactionIsolation with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.transactionIsolation = TEST_INT
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setTypeMap with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        val map: MutableMap<String, Class<*>> = mutableMapOf()
        restrictedConnection.typeMap = map
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setHoldability with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.holdability = TEST_INT
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setSchema with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.schema = TEST_STRING
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setNetworkTimeout with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.setNetworkTimeout({ println("I'm just an executor for this test...") }, TEST_INT)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setAutoCommit with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.autoCommit = true
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setReadOnly with target platform version of current corda version throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(PLATFORM_VERSION)
        restrictedConnection.isReadOnly = true
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `abort with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.abort { println("I'm just an executor for this test...") }
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `clearWarnings with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.clearWarnings()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `close with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.close()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `commit with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.commit()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setSavepoint with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.setSavepoint()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setSavepoint with name with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.setSavepoint(TEST_STRING)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `releaseSavepoint with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.releaseSavepoint(savePoint)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `rollback with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.rollback()
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `rollbackWithSavepoint with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.rollback(savePoint)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setCatalog with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.catalog = TEST_STRING
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setTransactionIsolation with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.transactionIsolation = TEST_INT
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setTypeMap with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        val map: MutableMap<String, Class<*>> = mutableMapOf()
        restrictedConnection.typeMap = map
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setHoldability with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.holdability = TEST_INT
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setSchema with target platform version of current 7 unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.schema = TEST_STRING
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setNetworkTimeout with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.setNetworkTimeout({ println("I'm just an executor for this test...") }, TEST_INT)
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setAutoCommit with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.autoCommit = true
    }

    @Test(expected = UnsupportedOperationException::class, timeout = 300_000)
    fun `setReadOnly with target platform version of 7 throws unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(7)
        restrictedConnection.isReadOnly = true
    }

    @Test(timeout = 300_000)
    fun `abort with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.abort { println("I'm just an executor for this test...") }
    }

    @Test(timeout = 300_000)
    fun `clearWarnings with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.clearWarnings()
    }

    @Test(timeout = 300_000)
    fun `close with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.close()
    }

    @Test(timeout = 300_000)
    fun `commit with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.commit()
    }

    @Test(timeout = 300_000)
    fun `setSavepoint with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.setSavepoint()
    }

    @Test(timeout = 300_000)
    fun `setSavepoint with name with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.setSavepoint(TEST_STRING)
    }

    @Test(timeout = 300_000)
    fun `releaseSavepoint with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.releaseSavepoint(savePoint)
    }

    @Test(timeout = 300_000)
    fun `rollback with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.rollback()
    }

    @Test(timeout = 300_000)
    fun `rollbackWithSavepoint with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.rollback(savePoint)
    }

    @Test(timeout = 300_000)
    fun `setCatalog with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.catalog = TEST_STRING
    }

    @Test(timeout = 300_000)
    fun `setTransactionIsolation with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.transactionIsolation = TEST_INT
    }

    @Test(timeout = 300_000)
    fun `setTypeMap with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        val map: MutableMap<String, Class<*>> = mutableMapOf()
        restrictedConnection.typeMap = map
    }

    @Test(timeout = 300_000)
    fun `setHoldability with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.holdability = TEST_INT
    }

    @Test(timeout = 300_000)
    fun `setSchema with target platform version of current 6 unsupported exception`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.schema = TEST_STRING
    }

    @Test(timeout = 300_000)
    fun `setNetworkTimeout with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.setNetworkTimeout({ println("I'm just an executor for this test...") }, TEST_INT)
    }

    @Test(timeout = 300_000)
    fun `setAutoCommit with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.autoCommit = true
    }

    @Test(timeout = 300_000)
    fun `setReadOnly with target platform version of 6 executes successfully`() {
        whenever(cordapp.targetPlatformVersion).thenReturn(6)
        restrictedConnection.isReadOnly = true
    }
}