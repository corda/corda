package net.corda.contracts.universal

import net.corda.core.contracts.Frequency
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.testing.transaction
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class RollOutTests {
    val TEST_TX_TIME_1: Instant get() = Instant.parse("2017-09-02T12:00:00.00Z")

    val contract = arrange {
        rollOut("2016-09-01".ld, "2017-09-01".ld, Frequency.Monthly) {
            actions {
                (acmeCorp or highStreetBank) may {
                    "transfer".givenThat(after(end)) {
                        highStreetBank.owes(acmeCorp, 10.K, USD)
                        next()
                    }
                }
            }
        }
    }


    val contract2 = arrange {
        rollOut("2016-09-01".ld, "2017-09-01".ld, Frequency.Monthly) {
            actions {
                (acmeCorp or highStreetBank) may {
                    "transfer".givenThat(after(end)) {
                        highStreetBank.owes(acmeCorp, 10.K, USD)
                        next()
                    }
                }
            }
        }
    }
    val stateStart = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contract)

    val contractStep1a = arrange {
        rollOut("2016-10-03".ld, "2017-09-01".ld, Frequency.Monthly) {
            actions {
                (acmeCorp or highStreetBank) may {
                    "transfer".givenThat(after(end)) {
                        highStreetBank.owes(acmeCorp, 10.K, USD)
                        next()
                    }
                }
            }
        }
    }

    val contractStep1b = arrange {
        highStreetBank.owes(acmeCorp, 10.K, USD)
    }

    val stateStep1a = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contractStep1a)
    val stateStep1b = UniversalContract.State(listOf(DUMMY_NOTARY.owningKey), contractStep1b)

    val contract_transfer1 = arrange {
        highStreetBank.owes(acmeCorp, 10.K, USD)
    }
    val contract_transfer2 = arrange {
        highStreetBank.owes(acmeCorp, 10.K, USD)
    }
    val contract_action1 = arrange {
        actions {
            highStreetBank may {
                "do it" anytime {
                    highStreetBank.owes(acmeCorp, 10.K, USD)
                }
            }
        }
    }
    val contract_action2 = arrange {
        actions {
            highStreetBank may {
                "do it" anytime {
                    highStreetBank.owes(acmeCorp, 10.K, USD)
                }
            }
        }
    }
    val contract_and1 = arrange {
        actions {
            highStreetBank may {
                "do it" anytime {
                    highStreetBank.owes(acmeCorp, 10.K, USD)
                }
            }
        }
        actions {
            acmeCorp may {
                "do it" anytime {
                    acmeCorp.owes(momAndPop, 10.K, USD)
                }
            }
        }
        next()

    }
    val contract_and2 = arrange {
        actions {
            highStreetBank may {
                "do it" anytime {
                    highStreetBank.owes(acmeCorp, 10.K, USD)
                }
            }
        }
        actions {
            acmeCorp may {
                "do it" anytime {
                    acmeCorp.owes(momAndPop, 10.K, USD)
                }
            }
        }
        next()
    }


    @Test
    fun `arrangement equality transfer`() {
        assertEquals(contract_transfer1, contract_transfer2)
    }

    @Test
    fun `arrangement equality action`() {
        assertEquals(contract_action1, contract_action2)
    }

    @Test
    fun `arrangement equality and`() {
        assertEquals(contract_and1, contract_and2)
    }

    @Test
    fun `arrangement equality complex`() {
        assertEquals(contract, contract2)
    }

    @Test
    fun issue() {
        transaction {
            output { stateStart }
            timestamp(TEST_TX_TIME_1)

            this `fails with` "transaction has a single command"

            tweak {
                command(acmeCorp.owningKey) { UniversalContract.Commands.Issue() }
                this `fails with` "the transaction is signed by all liable parties"
            }

            command(highStreetBank.owningKey) { UniversalContract.Commands.Issue() }

            this.verifies()
        }
    }

    @Test
    fun `execute`() {
        transaction {
            input { stateStart }
            output { stateStep1a }
            output { stateStep1b }
            timestamp(TEST_TX_TIME_1)

            /*   tweak {
                   command(highStreetBank.owningKey) { UniversalContract.Commands.Action("some undefined name") }
                   this `fails with` "action must be defined"
               }*/

            command(highStreetBank.owningKey) { UniversalContract.Commands.Action("transfer") }

            this.verifies()
        }
    }

}
