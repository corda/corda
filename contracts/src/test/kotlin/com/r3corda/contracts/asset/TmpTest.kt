package com.r3corda.contracts.asset

import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.`issued by`
import com.r3corda.core.testing.*
import org.junit.Test


class Asd {

    class Asd {

        @Test
        fun emptyLedger() {
            ledger {
            }
        }
//
//        @Test
//        fun simpleCashFails() {
//            ledger {
//                transaction {
//                    input(Cash.State(
//                            amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
//                            owner = DUMMY_PUBKEY_1
//                    ))
//                    this.verifies()
//                }
//            }
//        }

        @Test
        fun simpleCash() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                    this.verifies()
                }
            }
        }

        @Test
        fun simpleCashFailsWith() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                    this `fails with` "the amounts balance"
                }
            }
        }

        @Test
        fun simpleCashSuccess() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                    this `fails with` "the amounts balance"
                    output(inState.copy(owner = DUMMY_PUBKEY_2))
                    command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                    this.verifies()
                }
            }
        }

        @Test
        fun simpleCashTweakSuccess() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                    this `fails with` "the amounts balance"
                    output(inState.copy(owner = DUMMY_PUBKEY_2))

                    tweak {
                        command(DUMMY_PUBKEY_2) { Cash.Commands.Move() }
                        this `fails with` "the owning keys are the same as the signing keys"
                    }

                    command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                    this.verifies()
                }
            }
        }

        @Test
        fun simpleCashTweakSuccessTopLevelTransaction() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            transaction {
                input(inState)
                this `fails with` "the amounts balance"
                output(inState.copy(owner = DUMMY_PUBKEY_2))

                tweak {
                    command(DUMMY_PUBKEY_2) { Cash.Commands.Move() }
                    this `fails with` "the owning keys are the same as the signing keys"
                }

                command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this.verifies()
            }
        }


        @Test
        fun chainCash() {
            ledger {
                unverifiedTransaction {
                    output("MEGA_CORP cash") {
                        Cash.State(
                                amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MEGA_CORP_PUBKEY
                        )
                    }
                }

                transaction {
                    input("MEGA_CORP cash")
                    output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
                    command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                    this.verifies()
                }
            }
        }


        @Test
        fun chainCashDoubleSpend() {
            ledger {
                unverifiedTransaction {
                    output("MEGA_CORP cash") {
                        Cash.State(
                                amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MEGA_CORP_PUBKEY
                        )
                    }
                }

                transaction {
                    input("MEGA_CORP cash")
                    output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
                    command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                    this.verifies()
                }

                transaction {
                    input("MEGA_CORP cash")
                    // We send it to another pubkey so that the transaction is not identical to the previous one
                    output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
                    command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                    this.verifies()
                }
            }
        }

        @Test
        fun chainCashDoubleSpendFailsWith() {
            ledger {
                unverifiedTransaction {
                    output("MEGA_CORP cash") {
                        Cash.State(
                                amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MEGA_CORP_PUBKEY
                        )
                    }
                }

                transaction {
                    input("MEGA_CORP cash")
                    output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
                    command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                    this.verifies()
                }

                tweak {
                    transaction {
                        input("MEGA_CORP cash")
                        // We send it to another pubkey so that the transaction is not identical to the previous one
                        output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_2))
                        command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                        this.verifies()
                    }
                    this.fails()
                }

                this.verifies()
            }
        }
    }

}

