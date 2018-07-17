/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("StateSumming")

package net.corda.finance.utils

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Amount.Companion.sumOrNull
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.Amount.Companion.sumOrZero
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.Issued
import net.corda.core.identity.AbstractParty
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.Obligation
import java.util.*

/** A collection of utilities for summing states */

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
fun <T : Any> Iterable<ContractState>.sumFungibleOrNull(): Amount<Issued<T>>? {
    return filterIsInstance<FungibleAsset<T>>().map { it.amount }.sumOrNull()
}

/** Sums the asset states in the list, returning zero of the given token if there are none. */
fun <T : Any> Iterable<ContractState>.sumFungibleOrZero(token: Issued<T>): Amount<Issued<T>> {
    return filterIsInstance<FungibleAsset<T>>().map { it.amount }.sumOrZero(token)
}

/**
 * Sums the obligation states in the list, throwing an exception if there are none. All state objects in the
 * list are presumed to be nettable.
 */
fun <P : Any> Iterable<ContractState>.sumObligations(): Amount<Issued<Obligation.Terms<P>>> = filterIsInstance<Obligation.State<P>>().map { it.amount }.sumOrThrow()

/** Sums the obligation states in the list, returning null if there are none. */
fun <P : Any> Iterable<ContractState>.sumObligationsOrNull(): Amount<Issued<Obligation.Terms<P>>>? = filterIsInstance<Obligation.State<P>>().filter { it.lifecycle == Obligation.Lifecycle.NORMAL }.map { it.amount }.sumOrNull()

/** Sums the obligation states in the list, returning zero of the given product if there are none. */
fun <P : Any> Iterable<ContractState>.sumObligationsOrZero(issuanceDef: Issued<Obligation.Terms<P>>): Amount<Issued<Obligation.Terms<P>>> = filterIsInstance<Obligation.State<P>>().filter { it.lifecycle == Obligation.Lifecycle.NORMAL }.map { it.amount }.sumOrZero(issuanceDef)
