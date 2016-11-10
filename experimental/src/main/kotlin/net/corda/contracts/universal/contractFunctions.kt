package net.corda.contracts.universal

import net.corda.core.crypto.Party
import java.math.BigDecimal
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

fun swap(partyA: Party, amountA: BigDecimal, currencyA: Currency, partyB: Party, amountB: BigDecimal, currencyB: Currency) =
    arrange {
        partyA.gives(partyB, amountA, currencyA)
        partyB.gives(partyA, amountB, currencyB)
    }

fun fx_swap(expiry: String, notional: BigDecimal, strike: BigDecimal,
            foreignCurrency: Currency, domesticCurrency: Currency,
            partyA: Party, partyB: Party) = arrange {
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
        Action("execute", after(expiry), setOf(partyA, partyB),
                swap(partyA, BigDecimal(notional * strike), domesticCurrency, partyB, BigDecimal(notional), foreignCurrency))
