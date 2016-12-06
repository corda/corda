package net.corda.contracts.universal

import net.corda.core.crypto.Party
import net.corda.core.crypto.generateKeyPair
import org.junit.Test
import java.util.*

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
        actions {
            acmeCorp may {
                "payout".givenThat(acmeCorporationHasDefaulted and before("2017-09-01")) {
                    highStreetBank.owes(acmeCorp, 1.M, USD)
                }
            }
            highStreetBank may {
                "expire".givenThat(after("2017-09-01")) {
                    zero
                }
            }
        }
    }


    val american_fx_option = arrange {
        actions {
            acmeCorp may {
                "exercise" anytime {
                    highStreetBank.owes(acmeCorp, 1.M, USD)
                    acmeCorp.owes(highStreetBank, 1070.K, EUR)
                }
            }
            highStreetBank may {
                "expire".givenThat(after("2017-09-01")) {
                    zero
                }
            }
        }
    }


    val european_fx_option = arrange {
        actions {
            acmeCorp may {
                "exercise" anytime {
                    actions {
                        (acmeCorp or highStreetBank) may {
                            "execute".givenThat(after("2017-09-01")) {
                                highStreetBank.owes(acmeCorp, 1.M, USD)
                                acmeCorp.owes(highStreetBank, 1070.K, EUR)
                            }
                        }
                    }
                }
            }
            highStreetBank may {
                "expire".givenThat(after("2017-09-01")) {
                    zero
                }
            }
        }
    }


    /*   @Test
       fun `builder problem - should not compile`() {
           val arr = arrange {
               actions {
                   acmeCorp may {
                       "execute" anytime {
                           acmeCorp may {
                               "problem" anytime {
                                   highStreetBank.gives(acmeCorp, 1.M, USD)
                               }
                           }
                       }
                   }
               }
           }

           assert( arr is Actions )

           if (arr is Actions) {
               assert( arr.actions.size == 1)
           }
       }
   */
    @Test
    fun `builder problem - legal`() {
        val arr = arrange {
            actions {
                acmeCorp may {
                    "execute" anytime {
                        actions {
                            acmeCorp may {
                                "problem" anytime {
                                    highStreetBank.owes(acmeCorp, 1.M, USD)
                                }
                            }
                        }
                    }
                }
            }
        }

        assert(arr is Actions)

        if (arr is Actions) {
            assert(arr.actions.size == 1)
        }
    }

}
