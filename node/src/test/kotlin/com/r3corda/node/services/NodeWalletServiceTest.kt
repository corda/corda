package com.r3corda.node.services

import com.r3corda.contracts.testing.fillWithSomeTestCash
import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.SignedTransaction
import com.r3corda.core.node.services.TxWritableStorageService
import com.r3corda.core.node.services.WalletService
import com.r3corda.testing.node.MockServices
import com.r3corda.testing.node.makeTestDataSourceProperties
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.LogHelper
import com.r3corda.node.services.wallet.NodeWalletService
import com.r3corda.node.utilities.configureDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.util.*

class NodeWalletServiceTest {
    lateinit var dataSource: Closeable

    @Before
    fun setUp() {
        LogHelper.setLevel(NodeWalletService::class)
        dataSource = configureDatabase(makeTestDataSourceProperties()).first
    }

    @After
    fun tearDown() {
        dataSource.close()
        LogHelper.reset(NodeWalletService::class)
    }

    @Test
    fun `states not local to instance`() {
        val services1 = object : MockServices() {
            override val walletService: WalletService = NodeWalletService(this)

            override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                for (stx in txs) {
                    storageService.validatedTransactions.addTransaction(stx)
                    walletService.notify(stx.tx)
                }
            }
        }
        services1.fillWithSomeTestCash(100.DOLLARS, DUMMY_NOTARY, 3, 3, Random(0L))

        val w1 = services1.walletService.currentWallet
        assertThat(w1.states).hasSize(3)

        val originalStorage = services1.storageService
        val services2 = object : MockServices() {
            override val walletService: WalletService = NodeWalletService(this)

            // We need to be able to find the same transactions as before, too.
            override val storageService: TxWritableStorageService get() = originalStorage

            override fun recordTransactions(txs: Iterable<SignedTransaction>) {
                for (stx in txs) {
                    storageService.validatedTransactions.addTransaction(stx)
                    walletService.notify(stx.tx)
                }
            }
        }

        val w2 = services2.walletService.currentWallet
        assertThat(w2.states).hasSize(3)
    }
}