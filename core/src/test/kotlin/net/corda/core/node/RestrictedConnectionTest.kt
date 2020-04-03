package net.corda.core.node

import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import java.sql.Connection
import java.sql.Savepoint

class RestrictedConnectionTest {

    private val connection : Connection = mock()
    private val savePoint : Savepoint = mock()
    private val restrictedConnection : RestrictedConnection = RestrictedConnection(connection)

    companion object {
        private const val TEST_STRING : String = "test"
        private const val TEST_INT : Int = 1
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testAbort(){
        restrictedConnection.abort { println("I'm just an executor for this test...")}
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testClearWarnings(){
        restrictedConnection.clearWarnings()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testClose(){
        restrictedConnection.close()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testCommit(){
        restrictedConnection.commit()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetSavepoint(){
        restrictedConnection.setSavepoint()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetSavepointWithName(){
        restrictedConnection.setSavepoint(TEST_STRING)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testReleaseSavepoint(){
        restrictedConnection.releaseSavepoint(savePoint)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testRollback(){
        restrictedConnection.rollback()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testRollbackWithSavepoint(){
        restrictedConnection.rollback(savePoint)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetCatalog(){
        restrictedConnection.catalog = TEST_STRING
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetTransactionIsolation(){
        restrictedConnection.transactionIsolation = TEST_INT
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetTypeMap(){
        val map: MutableMap<String, Class<*>> = mutableMapOf()
        restrictedConnection.typeMap = map
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetHoldability(){
        restrictedConnection.holdability = TEST_INT
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetSchema(){
        restrictedConnection.schema = TEST_STRING
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetNetworkTimeout(){
        restrictedConnection.setNetworkTimeout({ println("I'm just an executor for this test...")}, TEST_INT)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetAutoCommit(){
        restrictedConnection.autoCommit = true
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testSetReadOnly(){
        restrictedConnection.isReadOnly = true
    }
}