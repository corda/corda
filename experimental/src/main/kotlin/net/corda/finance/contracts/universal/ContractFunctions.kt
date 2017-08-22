package net.corda.finance.contracts.universal

import net.corda.core.identity.Party
import java.math.BigDecimal
import java.util.*

fun swap(partyA: Party, amountA: BigDecimal, currencyA: Currency, partyB: Party, amountB: BigDecimal, currencyB: Currency) =
        arrange {
            partyA.owes(partyB, amountA, currencyA)
            partyB.owes(partyA, amountB, currencyB)
        }

fun fx_swap(expiry: String, notional: BigDecimal, strike: BigDecimal,
            foreignCurrency: Currency, domesticCurrency: Currency,
            partyA: Party, partyB: Party) =
        arrange {
            actions {
                (partyA or partyB).may {
                    "execute".givenThat(after(expiry)) {
                        swap(partyA, notional * strike, domesticCurrency, partyB, notional, foreignCurrency)
                    }
                }
            }
        }

// building an fx swap using abstract swap
fun fx_swap2(expiry: String, notional: Long, strike: Double,
             foreignCurrency: Currency, domesticCurrency: Currency,
             partyA: Party, partyB: Party) =
        Action("execute", after(expiry) and (signedBy(partyA) or signedBy(partyB)),
                swap(partyA, BigDecimal(notional * strike), domesticCurrency, partyB, BigDecimal(notional), foreignCurrency))
