package com.r3corda.contracts.universal

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.generateKeyPair
import org.junit.Test
import java.util.*

/**
 * Created by sofusmortensen on 08/06/16.
 */

// Test parties
val acmeCorp = Party("ACME Corporation", generateKeyPair().public)
val highStreetBank = Party("High Street Bank", generateKeyPair().public)
val momAndPop = Party("Mom and Pop", generateKeyPair().public)

val acmeCorporationHasDefaulted = DummyPerceivable<Boolean>()


// Currencies
val USD: Currency = Currency.getInstance("USD")
val GBP: Currency = Currency.getInstance("GBP")
val EUR: Currency = Currency.getInstance("EUR")
val KRW: Currency = Currency.getInstance("KRW")


class ContractDefinition {


    val cds_contract = arrange {
        acmeCorp.may {
            "payout".givenThat(acmeCorporationHasDefaulted and before("2017-09-01")) {
                highStreetBank.gives(acmeCorp, 1.M, USD)
            }
        }
        highStreetBank.may {
            "expire".givenThat(after("2017-09-01")) {
                zero
            }
        }
    }


    val american_fx_option = arrange {
        acmeCorp.may {
            "exercise".anytime {
                highStreetBank.gives(acmeCorp, 1.M, EUR)
                acmeCorp.gives(highStreetBank, 1200.K, USD)
            }
        }
        highStreetBank.may {
            "expire".givenThat(after("2017-09-01")) {
                zero
            }
        }
    }


    val european_fx_option = arrange {
        acmeCorp.may {
            "exercise".anytime {
                (acmeCorp or highStreetBank).may {
                    "execute".givenThat(after("2017-09-01")) {
                        highStreetBank.gives(acmeCorp, 1.M, EUR)
                        acmeCorp.gives(highStreetBank, 1200.K, USD)
                    }
                }
            }
        }
        highStreetBank.may {
            "expire".givenThat(after("2017-09-01")) {
                zero
            }
        }
    }


    @Test
    fun test() {

    }
}