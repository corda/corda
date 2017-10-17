package com.r3.corda.enterprise.perftestcordapp.utils

import com.r3.corda.enterprise.perftestcordapp.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Amount.Companion.sumOrNull
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.Amount.Companion.sumOrZero
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.Issued
import net.corda.core.identity.AbstractParty
import java.util.*

/**
 * Sums the cash states in the list belonging to a single owner, throwing an exception
 * if there are none, or if any of the cash states cannot be added together (i.e. are
 * different currencies or issuers).
 */
fun Iterable<ContractState>.sumCashBy(owner: AbstractParty): Amount<Issued<Currency>> = filterIsInstance<Cash.State>().filter { it.owner == owner }.map { it.amount }.sumOrThrow()

/**
 * Sums the cash states in the list, throwing an exception if there are none, or if any of the cash
 * states cannot be added together (i.e. are different currencies or issuers).
 */
fun Iterable<ContractState>.sumCash(): Amount<Issued<Currency>> = filterIsInstance<Cash.State>().map { it.amount }.sumOrThrow()

/** Sums the cash states in the list, returning null if there are none. */
fun Iterable<ContractState>.sumCashOrNull(): Amount<Issued<Currency>>? = filterIsInstance<Cash.State>().map { it.amount }.sumOrNull()

/** Sums the cash states in the list, returning zero of the given currency+issuer if there are none. */
fun Iterable<ContractState>.sumCashOrZero(currency: Issued<Currency>): Amount<Issued<Currency>> {
    return filterIsInstance<Cash.State>().map { it.amount }.sumOrZero(currency)
}

/** Sums the asset states in the list, returning null if there are none. */
fun <T : Any> Iterable<ContractState>.sumFungibleOrNull() = filterIsInstance<FungibleAsset<T>>().map { it.amount }.sumOrNull()

/** Sums the asset states in the list, returning zero of the given token if there are none. */
fun <T : Any> Iterable<ContractState>.sumFungibleOrZero(token: Issued<T>) = filterIsInstance<FungibleAsset<T>>().map { it.amount }.sumOrZero(token)

