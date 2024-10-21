package net.corda.finance.contracts.universal

import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Test parties
val acmeCorp = TestIdentity(CordaX500Name("Alice Corp", "Madrid", "ES")).party
val highStreetBank = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
val momAndPop = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).party
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

    /*   @Test(timeout=300_000)
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
    @Test(timeout=300_000)
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

        assertEquals(1, arr.actions.size)
    }
}
