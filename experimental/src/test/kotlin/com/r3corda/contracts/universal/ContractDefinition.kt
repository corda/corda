package com.r3corda.contracts.universal

import com.r3corda.core.contracts.Amount
import com.r3corda.core.contracts.Tenor
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Created by sofusmortensen on 08/06/16.
 */

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
        "payout".givenThat( acmeCorporationHasDefaulted and before("2017-09-01") ) {
            wileECoyote.gives(roadRunner, 1.M*USD)
        }
    } or wileECoyote.may {
        "expire".givenThat( after("2017-09-01") ) {}
    }


    val american_fx_option = roadRunner.may {
        "exercise".anytime {
            wileECoyote.gives(roadRunner, 1.M*EUR)
            roadRunner.gives(wileECoyote, 1200.K*USD)
        }
    } or wileECoyote.may {
        "expire".givenThat(after("2017-09-01")) {}
    }


    val european_fx_option = roadRunner.may {
        "exercise".anytime {
            (roadRunner or wileECoyote).may {
                "execute".givenThat( after("2017-09-01") ) {
                    wileECoyote.gives( roadRunner, 1.M*EUR )
                    roadRunner.gives( wileECoyote, 1200.K*USD )
                }
            }
        }
    } or wileECoyote.may {
        "expire".givenThat( after("2017-09-01")) {}
    }


    @Test
    fun test() {

    }
}