@file:JvmName("GetBalances")

package net.corda.finance.contracts

import net.corda.core.contracts.Amount
import net.corda.core.contracts.FungibleAsset
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.builder
import net.corda.finance.schemas.CashSchemaV1
import java.util.*
import kotlin.collections.LinkedHashMap

private fun generateCashSumCriteria(currency: Currency): QueryCriteria {
    val sum = builder { CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency)) }
    val sumCriteria = QueryCriteria.VaultCustomQueryCriteria(sum)

    val ccyIndex = builder { CashSchemaV1.PersistentCashState::currency.equal(currency.currencyCode) }
    // This query should only return cash states the calling node is a participant of (meaning they can be modified/spent).
    val ccyCriteria = QueryCriteria.VaultCustomQueryCriteria(ccyIndex, isModifiable = Vault.StateModificationStatus.MODIFIABLE)
    return sumCriteria.and(ccyCriteria)
}

private fun generateCashSumsCriteria(): QueryCriteria {
    val sum = builder {
        CashSchemaV1.PersistentCashState::pennies.sum(groupByColumns = listOf(CashSchemaV1.PersistentCashState::currency),
                orderBy = Sort.Direction.DESC)
    }
    // This query should only return cash states the calling node is a participant of (meaning they can be modified/spent).
    return QueryCriteria.VaultCustomQueryCriteria(sum, isModifiable = Vault.StateModificationStatus.MODIFIABLE)
}

private fun rowsToAmount(currency: Currency, rows: Vault.Page<FungibleAsset<*>>): Amount<Currency> {
    return if (rows.otherResults.isEmpty()) {
        Amount(0L, currency)
    } else {
        require(rows.otherResults.size == 2)
        require(rows.otherResults[1] == currency.currencyCode)
        val quantity = rows.otherResults[0] as Long
        Amount(quantity, currency)
    }
}

private fun rowsToBalances(rows: List<Any>): Map<Currency, Amount<Currency>> {
    val balances = LinkedHashMap<Currency, Amount<Currency>>()
    for (index in 0 until rows.size step 2) {
        val ccy = Currency.getInstance(rows[index + 1] as String)
        balances[ccy] = Amount(rows[index] as Long, ccy)
    }
    return balances
}

fun CordaRPCOps.getCashBalance(currency: Currency): Amount<Currency> {
    val results = this.vaultQueryByCriteria(generateCashSumCriteria(currency), FungibleAsset::class.java)
    return rowsToAmount(currency, results)
}

fun ServiceHub.getCashBalance(currency: Currency): Amount<Currency> {
    val results = this.vaultService.queryBy<FungibleAsset<*>>(generateCashSumCriteria(currency))
    return rowsToAmount(currency, results)
}

fun CordaRPCOps.getCashBalances(): Map<Currency, Amount<Currency>> {
    val sums = this.vaultQueryBy<FungibleAsset<*>>(generateCashSumsCriteria()).otherResults
    return rowsToBalances(sums)
}

fun ServiceHub.getCashBalances(): Map<Currency, Amount<Currency>> {
    val sums = this.vaultService.queryBy<FungibleAsset<*>>(generateCashSumsCriteria()).otherResults
    return rowsToBalances(sums)
}

