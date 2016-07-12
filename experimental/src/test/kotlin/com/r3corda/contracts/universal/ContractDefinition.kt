package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Amount
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * Created by sofusmortensen on 08/06/16.
 */


class DummyPerceivable<T> : Perceivable<T>


// observable of type T
// example:
val acmeCorporationHasDefaulted = DummyPerceivable<Boolean>()


fun libor(@Suppress("UNUSED_PARAMETER") amount: Amount<Currency>, @Suppress("UNUSED_PARAMETER") start: String, @Suppress("UNUSED_PARAMETER") end: String) : Perceivable<Amount<Currency>> = DummyPerceivable()
fun libor(@Suppress("UNUSED_PARAMETER") amount: Amount<Currency>, @Suppress("UNUSED_PARAMETER") start: Perceivable<Instant>, @Suppress("UNUSED_PARAMETER") end: Perceivable<Instant>) : Perceivable<Amount<Currency>> = DummyPerceivable()

fun interest(@Suppress("UNUSED_PARAMETER") rate: Amount<Currency>, @Suppress("UNUSED_PARAMETER") dayCountConvention: String, @Suppress("UNUSED_PARAMETER") interest: Double /* todo -  appropriate type */,
             @Suppress("UNUSED_PARAMETER") start: String, @Suppress("UNUSED_PARAMETER") end: String) : Perceivable<Amount<Currency>> = DummyPerceivable()
fun interest(@Suppress("UNUSED_PARAMETER") rate: Amount<Currency>, @Suppress("UNUSED_PARAMETER") dayCountConvention: String, @Suppress("UNUSED_PARAMETER") interest: Double /* todo -  appropriate type */,
             @Suppress("UNUSED_PARAMETER") start: Perceivable<Instant>, @Suppress("UNUSED_PARAMETER") end: Perceivable<Instant>) : Perceivable<Amount<Currency>> = DummyPerceivable()

// Test parties
val roadRunner = Party("Road Runner", generateKeyPair().public)
val wileECoyote = Party("Wile E. Coyote", generateKeyPair().public)
val porkyPig = Party("Porky Pig", generateKeyPair().public)


// Currencies
val USD = Currency.getInstance("USD")
val GBP = Currency.getInstance("GBP")
val EUR = Currency.getInstance("EUR")
val KRW = Currency.getInstance("KRW")


class ContractDefinition {


    val cds_contract = roadRunner.may {
        "payout".givenThat( acmeCorporationHasDefaulted and before("01/09/2017") ) {
            wileECoyote.gives(roadRunner, 1.M*USD)
        }
    } or wileECoyote.may {
        "expire".givenThat( after("01/09/2017") ) {}
    }


    val american_fx_option = roadRunner.may {
        "exercise".anytime {
            wileECoyote.gives(roadRunner, 1.M*EUR)
            roadRunner.gives(wileECoyote, 1200.K*USD)
        }
    } or wileECoyote.may {
        "expire".givenThat(after("01/09/2017")) {}
    }


    val european_fx_option = roadRunner.may {
        "exercise".anytime {
            (roadRunner or wileECoyote).may {
                "execute".givenThat( after("01/09/2017") ) {
                    wileECoyote.gives( roadRunner, 1.M*EUR )
                    roadRunner.gives( wileECoyote, 1200.K*USD )
                }
            }
        }
    } or wileECoyote.may {
        "expire".givenThat( after("01/09/2017")) {}
    }


    @Test
    fun test() {

    }
}