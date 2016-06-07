package com.r3corda.node.services.persistence

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.google.common.primitives.Ints
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class PerFileTransactionStorageTests {

    val fileSystem = Jimfs.newFileSystem(unix())
    val storeDir = fileSystem.getPath("store")
    lateinit var transactionStorage: PerFileTransactionStorage

    @Before
    fun setUp() {
        newTransactionStorage()
    }

    @After
    fun cleanUp() {
        fileSystem.close()
    }

    @Test
    fun `empty store`() {
        assertThat(transactionStorage.getTransaction(newTransaction().id)).isNull()
        assertThat(transactionStorage.transactions).isEmpty()
        newTransactionStorage()
        assertThat(transactionStorage.transactions).isEmpty()
    }

    @Test
    fun `one transaction`() {
        val transaction = newTransaction()
        transactionStorage.addTransaction(transaction)
        assertTransactionIsRetrievable(transaction)
        assertThat(transactionStorage.transactions).containsExactly(transaction)
        newTransactionStorage()
        assertTransactionIsRetrievable(transaction)
        assertThat(transactionStorage.transactions).containsExactly(transaction)
    }

    @Test
    fun `two transactions across restart`() {
        val firstTransaction = newTransaction()
        val secondTransaction = newTransaction()
        transactionStorage.addTransaction(firstTransaction)
        newTransactionStorage()
        transactionStorage.addTransaction(secondTransaction)
        assertTransactionIsRetrievable(firstTransaction)
        assertTransactionIsRetrievable(secondTransaction)
        assertThat(transactionStorage.transactions).containsOnly(firstTransaction, secondTransaction)
    }

    @Test
    fun `non-transaction files are ignored`() {
        val transactions = newTransaction()
        transactionStorage.addTransaction(transactions)
        Files.write(storeDir.resolve("random-non-tx-file"), "this is not a transaction!!".toByteArray())
        newTransactionStorage()
        assertThat(transactionStorage.transactions).containsExactly(transactions)
    }

    private fun newTransactionStorage() {
        transactionStorage = PerFileTransactionStorage(storeDir)
    }

    private fun assertTransactionIsRetrievable(transaction: SignedTransaction) {
        assertThat(transactionStorage.getTransaction(transaction.id)).isEqualTo(transaction)
    }

    private var txCount = 0
    private fun newTransaction() = SignedTransaction(
            SerializedBytes(Ints.toByteArray(++txCount)),
            listOf(DigitalSignature.WithKey(NullPublicKey, ByteArray(1))))

}