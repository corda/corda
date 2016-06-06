package com.r3corda.contracts.generic

import com.r3corda.core.contracts.Amount
import com.r3corda.core.crypto.Party
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

fun swap(partyA: Party, amountA: Amount<Currency>, partyB: Party, amountB: Amount<Currency>) =
        Transfer(amountA, partyA, partyB) and Transfer(amountB, partyB, partyA)

fun fx_swap(expiry: String, notional: Long, strike: Double,
            foreignCurrency: Currency, domesticCurrency: Currency,
            partyA: Party, partyB: Party) =
        Action("execute", after(expiry), arrayOf(partyA, partyB),
                Transfer(notional * strike * domesticCurrency, partyA, partyB)
                        and Transfer(notional * foreignCurrency, partyB, partyA))

// building an fx swap using abstract swap
fun fx_swap2(expiry: String, notional: Long, strike: Double,
             foreignCurrency: Currency, domesticCurrency: Currency,
             partyA: Party, partyB: Party) =
        Action("execute", after(expiry), arrayOf(partyA, partyB),
                swap(partyA, notional * strike * domesticCurrency, partyB, notional * foreignCurrency))
