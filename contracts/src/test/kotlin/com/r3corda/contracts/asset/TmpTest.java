package com.r3corda.contracts.asset;

import kotlin.Unit;
import org.junit.Test;

import static com.r3corda.core.testing.JavaTestHelpers.*;
import static com.r3corda.core.contracts.JavaTestHelpers.*;

public class TmpTest {

    public static class Asd {
        @Test
        public void emptyLedger() {
            ledger(l -> {
                return Unit.INSTANCE;
            });
        }
//
//        @Test
//        public void simpleCashDoesntCompile() {
//            Cash.State inState = new Cash.State(
//                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
//                    getDUMMY_PUBKEY_1()
//            );
//            ledger(l -> {
//                l.transaction(tx -> {
//                    tx.input(inState);
//                });
//                return Unit.INSTANCE;
//            });
//        }

        @Test
        public void simpleCash() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                    return tx.verifies();
                });
                return Unit.INSTANCE;
            });
        }

        @Test
        public void simpleCashFailsWith() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                    return tx.failsWith("the amounts balance");
                });
                return Unit.INSTANCE;
            });
        }

        @Test
        public void simpleCashSuccess() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                    tx.failsWith("the amounts balance");
                    tx.output(inState.copy(inState.getAmount(), getDUMMY_PUBKEY_2()));
                    tx.command(getDUMMY_PUBKEY_1(), new Cash.Commands.Move());
                    return tx.verifies();
                });
                return Unit.INSTANCE;
            });
        }

        @Test
        public void simpleCashTweakSuccess() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                    tx.failsWith("the amounts balance");
                    tx.output(inState.copy(inState.getAmount(), getDUMMY_PUBKEY_2()));

                    tx.tweak(tw -> {
                        tw.command(getDUMMY_PUBKEY_2(), new Cash.Commands.Move());
                        return tw.failsWith("the owning keys are the same as the signing keys");
                    });
                    tx.command(getDUMMY_PUBKEY_1(), new Cash.Commands.Move());
                    return tx.verifies();
                });
                return Unit.INSTANCE;
            });
        }

        @Test
        public void simpleCashTweakSuccessTopLevelTransaction() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            transaction(tx -> {
                tx.input(inState);
                tx.failsWith("the amounts balance");
                tx.output(inState.copy(inState.getAmount(), getDUMMY_PUBKEY_2()));

                tx.tweak(tw -> {
                    tw.command(getDUMMY_PUBKEY_2(), new Cash.Commands.Move());
                    return tw.failsWith("the owning keys are the same as the signing keys");
                });
                tx.command(getDUMMY_PUBKEY_1(), new Cash.Commands.Move());
                return tx.verifies();
            });
        }


        @Test
        public void chainCash() {
            ledger(l -> {
                l.unverifiedTransaction(tx -> {
                    tx.output("MEGA_CORP cash",
                            new Cash.State(
                                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                                    getMEGA_CORP_PUBKEY()
                            )
                    );
                    return Unit.INSTANCE;
                });

                l.transaction(tx -> {
                    tx.input("MEGA_CORP cash");
                    Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                    tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_1()));
                    tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tx.verifies();
                });

                return Unit.INSTANCE;
            });
        }

        @Test
        public void chainCashDoubleSpend() {
            ledger(l -> {
                l.unverifiedTransaction(tx -> {
                    tx.output("MEGA_CORP cash",
                            new Cash.State(
                                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                                    getMEGA_CORP_PUBKEY()
                            )
                    );
                    return Unit.INSTANCE;
                });

                l.transaction(tx -> {
                    tx.input("MEGA_CORP cash");
                    Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                    tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_1()));
                    tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tx.verifies();
                });

                l.transaction(tx -> {
                    tx.input("MEGA_CORP cash");
                    Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                    // We send it to another pubkey so that the transaction is not identical to the previous one
                    tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_2()));
                    tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tx.verifies();
                });

                return Unit.INSTANCE;
            });
        }

        @Test
        public void chainCashDoubleSpendFailsWith() {
            ledger(l -> {
                l.unverifiedTransaction(tx -> {
                    tx.output("MEGA_CORP cash",
                            new Cash.State(
                                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                                    getMEGA_CORP_PUBKEY()
                            )
                    );
                    return Unit.INSTANCE;
                });

                l.transaction(tx -> {
                    tx.input("MEGA_CORP cash");
                    Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                    tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_1()));
                    tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tx.verifies();
                });

                l.tweak(lw -> {
                    lw.transaction(tx -> {
                        tx.input("MEGA_CORP cash");
                        Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                        // We send it to another pubkey so that the transaction is not identical to the previous one
                        tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_2()));
                        tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                        return tx.verifies();
                    });
                    lw.fails();
                    return Unit.INSTANCE;
                });

                l.verifies();
                return Unit.INSTANCE;
            });
        }


    }
}
