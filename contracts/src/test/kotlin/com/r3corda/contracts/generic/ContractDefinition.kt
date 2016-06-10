package com.r3corda.contracts.generic

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import org.junit.Test
import java.math.BigDecimal
import java.util.*

/**
 * Created by sofusmortensen on 08/06/16.
 */


class DummyObservable<T> : Observable<T>


// observable of type T
// example:
val acmeCorporationHasDefaulted = DummyObservable<Boolean>()

// example:
val euribor3M = DummyObservable<BigDecimal>()


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