package net.corda.node.services.vault

import com.codahale.metrics.Gauge
import net.corda.core.node.services.VaultService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.utilities.CordaPersistence
import java.util.*

/**
 * This class observes the vault and reflect current cash balances as exposed metrics in the monitoring service.
 */
class CashBalanceAsMetricsObserver(val serviceHubInternal: ServiceHubInternal, val database: CordaPersistence) {
    init {
        // TODO: Need to consider failure scenarios.  This needs to run if the TX is successfully recorded
        serviceHubInternal.vaultService.updates.subscribe { _ ->
            exportCashBalancesViaMetrics(serviceHubInternal.vaultService)
        }
    }

    private class BalanceMetric : Gauge<Long> {
        @Volatile var pennies = 0L
        override fun getValue(): Long? = pennies
    }

    private val balanceMetrics = HashMap<Currency, BalanceMetric>()

    private fun exportCashBalancesViaMetrics(vault: VaultService) {
        // This is just for demo purposes. We probably shouldn't expose balances via JMX in a real node as that might
        // be commercially sensitive info that the sysadmins aren't even meant to know.
        //
        // Note: exported as pennies.
        val m = serviceHubInternal.monitoringService.metrics
        database.transaction {
            for ((key, value) in vault.cashBalances) {
                val metric = balanceMetrics.getOrPut(key) {
                    val newMetric = BalanceMetric()
                    m.register("VaultBalances.${key}Pennies", newMetric)
                    newMetric
                }
                metric.pennies = value.quantity
            }
        }
    }
}
