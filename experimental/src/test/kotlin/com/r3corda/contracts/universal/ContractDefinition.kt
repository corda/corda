package com.r3corda.contracts.universal

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import org.junit.Test
import java.util.*

/**
 * Created by sofusmortensen on 08/06/16.
 */

// Test parties
val roadRunner = Party("Road Runner", generateKeyPair().public)
val wileECoyote = Party("Wile E. Coyote", generateKeyPair().public)
val porkyPig = Party("Porky Pig", generateKeyPair().public)

val acmeCorporationHasDefaulted = DummyPerceivable<Boolean>()


// Currencies
val USD: Currency = Currency.getInstance("USD")
val GBP: Currency = Currency.getInstance("GBP")
val EUR: Currency = Currency.getInstance("EUR")
val KRW: Currency = Currency.getInstance("KRW")


class ContractDefinition {


    val cds_contract = arrange {
        roadRunner.may {
            "payout".givenThat(acmeCorporationHasDefaulted and before("2017-09-01")) {
                wileECoyote.gives(roadRunner, 1.M, USD)
            }
        } or wileECoyote.may {
            "expire".givenThat(after("2017-09-01")) {
                zero
            }
        }
    }


    val american_fx_option = arrange {
        roadRunner.may {
            "exercise".anytime {
                wileECoyote.gives(roadRunner, 1.M, EUR)
                roadRunner.gives(wileECoyote, 1200.K, USD)
            }
        } or wileECoyote.may {
            "expire".givenThat(after("2017-09-01")) {
                zero
            }
        }
    }


    val european_fx_option = arrange {
        roadRunner.may {
            "exercise".anytime {
                (roadRunner or wileECoyote).may {
                    "execute".givenThat(after("2017-09-01")) {
                        wileECoyote.gives(roadRunner, 1.M, EUR)
                        roadRunner.gives(wileECoyote, 1200.K, USD)
                    }
                }
            }
        } or wileECoyote.may {
            "expire".givenThat(after("2017-09-01")) {
                zero
            }
        }
    }


    @Test
    fun test() {

    }
}