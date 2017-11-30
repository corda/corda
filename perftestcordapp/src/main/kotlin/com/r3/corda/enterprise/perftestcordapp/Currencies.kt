@file:JvmName("Currencies")

package com.r3.corda.enterprise.perftestcordapp

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import java.math.BigDecimal
import java.util.*

@JvmField val USD: Currency = Currency.getInstance("USD")
@JvmField val GBP: Currency = Currency.getInstance("GBP")
@JvmField val EUR: Currency = Currency.getInstance("EUR")
@JvmField val CHF: Currency = Currency.getInstance("CHF")
@JvmField val JPY: Currency = Currency.getInstance("JPY")
@JvmField val RUB: Currency = Currency.getInstance("RUB")

fun <T : Any> AMOUNT(amount: Int, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount.toLong()), token)
fun <T : Any> AMOUNT(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : Any> AMOUNT(amount: Long, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun DOLLARS(amount: Int): Amount<Currency> = AMOUNT(amount, USD)
fun DOLLARS(amount: Double): Amount<Currency> = AMOUNT(amount, USD)
fun DOLLARS(amount: Long): Amount<Currency> = AMOUNT(amount, USD)
fun POUNDS(amount: Int): Amount<Currency> = AMOUNT(amount, GBP)
fun POUNDS(amount: Double): Amount<Currency> = AMOUNT(amount, GBP)
fun POUNDS(amount: Long): Amount<Currency> = AMOUNT(amount, GBP)
fun SWISS_FRANCS(amount: Int): Amount<Currency> = AMOUNT(amount, CHF)
fun SWISS_FRANCS(amount: Double): Amount<Currency> = AMOUNT(amount, CHF)
fun SWISS_FRANCS(amount: Long): Amount<Currency> = AMOUNT(amount, CHF)

val Int.DOLLARS: Amount<Currency> get() = DOLLARS(this)
val Double.DOLLARS: Amount<Currency> get() = DOLLARS(this)
val Long.DOLLARS: Amount<Currency> get() = DOLLARS(this)
val Int.POUNDS: Amount<Currency> get() = POUNDS(this)
val Double.POUNDS: Amount<Currency> get() = POUNDS(this)
val Long.POUNDS: Amount<Currency> get() = POUNDS(this)
val Int.SWISS_FRANCS: Amount<Currency> get() = SWISS_FRANCS(this)
val Double.SWISS_FRANCS: Amount<Currency> get() = SWISS_FRANCS(this)
val Long.SWISS_FRANCS: Amount<Currency> get() = SWISS_FRANCS(this)

infix fun Currency.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Amount<Currency>.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Currency.issuedBy(deposit: PartyAndReference) = Issued(deposit, this)
infix fun Amount<Currency>.issuedBy(deposit: PartyAndReference) = Amount(quantity, displayTokenSize, token.issuedBy(deposit))

