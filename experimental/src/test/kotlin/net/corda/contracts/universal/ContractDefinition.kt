package net.corda.contracts.universal

import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.Party
import net.corda.testing.ALICE
import net.corda.testing.MEGA_CORP
import net.corda.testing.MINI_CORP
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Test parties
val acmeCorp = Party(ALICE.name, generateKeyPair().public)
val highStreetBank = Party(MEGA_CORP.name, generateKeyPair().public)
val momAndPop = Party(MINI_CORP.name, generateKeyPair().public)

val acmeCorporationHasDefaulted = TerminalEvent(acmeCorp, generateKeyPair().public)


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

           assertTrue( arr is Actions )

           if (arr is Actions) {
               assertEquals(1, arr.actions.size)
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

        assertTrue(arr is Actions)

        if (arr is Actions) {
            assertEquals(1, arr.actions.size)
        }
    }

}
