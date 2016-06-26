package com.r3corda.node.services.wallet

import com.codahale.metrics.Gauge
import com.r3corda.contracts.cash.cashBalances
import com.r3corda.core.node.services.Wallet
import com.r3corda.node.services.api.ServiceHubInternal
import java.util.*

/**
 * This class observes the wallet and reflect current cash balances as exposed metrics in the monitoring service.
 */
class CashBalanceAsMetricsObserver(val serviceHubInternal: ServiceHubInternal) {
    init {
        // TODO: Need to consider failure scenarios.  This needs to run if the TX is successfully recorded
        serviceHubInternal.walletService.updates.subscribe { update ->
            exportCashBalancesViaMetrics(serviceHubInternal.walletService.currentWallet)
        }
    }

    private class BalanceMetric : Gauge<Long> {
        @Volatile var pennies = 0L
        override fun getValue(): Long? = pennies
    }

    private val balanceMetrics = HashMap<Currency, BalanceMetric>()

    private fun exportCashBalancesViaMetrics(wallet: Wallet) {
        // This is just for demo purposes. We probably shouldn't expose balances via JMX in a real node as that might
        // be commercially sensitive info that the sysadmins aren't even meant to know.
        //
        // Note: exported as pennies.
        val m = serviceHubInternal.monitoringService.metrics
        for (balance in wallet.cashBalances) {
            val metric = balanceMetrics.getOrPut(balance.key) {
                val newMetric = BalanceMetric()
                m.register("WalletBalances.${balance.key}Pennies", newMetric)
                newMetric
            }
            metric.pennies = balance.value.quantity
        }
    }
}