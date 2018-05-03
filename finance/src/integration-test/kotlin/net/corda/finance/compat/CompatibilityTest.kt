package net.corda.finance.compat

import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// TODO: If this type of testing gets momentum, we can create a mini-framework that rides through list of files
// and performs necessary validation on all of them.
class CompatibilityTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun issueCashTansactionReadTest() {
        val inputStream = javaClass.classLoader.getResourceAsStream("compatibilityData/v3/node_transaction.dat")
        assertNotNull(inputStream)
        val inByteArray: ByteArray = inputStream.readBytes()
        val transaction = inByteArray.deserialize<SignedTransaction>(context = SerializationDefaults.STORAGE_CONTEXT)
        assertNotNull(transaction)
        val commands = transaction.tx.commands
        assertEquals(1, commands.size)
        assertTrue(commands.first().value is Cash.Commands.Issue)

        // Serialize back and check that representation is byte-to-byte identical to what it was originally.
        val serializedForm = transaction.serialize(context = SerializationDefaults.STORAGE_CONTEXT)
        assertTrue(inByteArray.contentEquals(serializedForm.bytes))
    }
}