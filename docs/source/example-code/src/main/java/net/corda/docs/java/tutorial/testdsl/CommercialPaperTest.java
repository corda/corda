package net.corda.docs.java.tutorial.testdsl;

import kotlin.Unit;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.finance.contracts.ICommercialPaperState;
import net.corda.finance.contracts.JavaCommercialPaper;
import net.corda.finance.contracts.asset.Cash;
import org.junit.Test;

import java.time.temporal.ChronoUnit;

import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.finance.Currencies.issuedBy;
import static net.corda.finance.contracts.JavaCommercialPaper.JCP_PROGRAM_ID;
import static net.corda.testing.CoreTestUtils.*;
import static net.corda.testing.NodeTestUtils.ledger;
import static net.corda.testing.NodeTestUtils.transaction;
import static net.corda.testing.TestConstants.*;

public class CommercialPaperTest {
    private final OpaqueBytes defaultRef = new OpaqueBytes(new byte[]{123});

    // DOCSTART 1
    private ICommercialPaperState getPaper() {
        return new JavaCommercialPaper.State(
                getMEGA_CORP().ref(defaultRef),
                getMEGA_CORP(),
                issuedBy(DOLLARS(1000), getMEGA_CORP().ref(defaultRef)),
                getTEST_TX_TIME().plus(7, ChronoUnit.DAYS)
        );
    }
    // DOCEND 1

    // DOCSTART 2
    @Test
    public void simpleCP() {
        ICommercialPaperState inState = getPaper();
        ledger(l -> {
            l.transaction(tx -> {
                tx.attachments(JCP_PROGRAM_ID);
                tx.input(JCP_PROGRAM_ID, inState);
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 2

    // DOCSTART 3
    @Test
    public void simpleCPMove() {
        ICommercialPaperState inState = getPaper();
        ledger(l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Move());
                tx.attachments(JCP_PROGRAM_ID);
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 3

    // DOCSTART 4
    @Test
    public void simpleCPMoveFails() {
        ICommercialPaperState inState = getPaper();
        ledger(l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Move());
                tx.attachments(JCP_PROGRAM_ID);
                return tx.failsWith("the state is propagated");
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 4

    // DOCSTART 5
    @Test
    public void simpleCPMoveSuccess() {
        ICommercialPaperState inState = getPaper();
        ledger(l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Move());
                tx.attachments(JCP_PROGRAM_ID);
                tx.failsWith("the state is propagated");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inState.withOwner(getALICE()));
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 5

    // DOCSTART 6
    @Test
    public void simpleIssuanceWithTweak() {
        ledger(l -> {
            l.transaction(tx -> {
                tx.output(JCP_PROGRAM_ID, "paper", getPaper()); // Some CP is issued onto the ledger by MegaCorp.
                tx.attachments(JCP_PROGRAM_ID);
                tx.tweak(tw -> {
                    tw.command(getBIG_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Issue());
                    tw.timeWindow(getTEST_TX_TIME());
                    return tw.failsWith("output states are issued by a command signer");
                });
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Issue());
                tx.timeWindow(getTEST_TX_TIME());
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 6

    // DOCSTART 7
    @Test
    public void simpleIssuanceWithTweakTopLevelTx() {
        transaction(tx -> {
            tx.output(JCP_PROGRAM_ID, "paper", getPaper()); // Some CP is issued onto the ledger by MegaCorp.
            tx.attachments(JCP_PROGRAM_ID);
            tx.tweak(tw -> {
                tw.command(getBIG_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Issue());
                tw.timeWindow(getTEST_TX_TIME());
                return tw.failsWith("output states are issued by a command signer");
            });
            tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Issue());
            tx.timeWindow(getTEST_TX_TIME());
            return tx.verifies();
        });
    }
    // DOCEND 7

    // DOCSTART 8
    @Test
    public void chainCommercialPaper() {
        PartyAndReference issuer = getMEGA_CORP().ref(defaultRef);
        ledger(l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), getALICE()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(JCP_PROGRAM_ID, "paper", getPaper());
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(getTEST_TX_TIME());
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), getMEGA_CORP()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(getALICE()));
                tx.command(getALICE_PUBKEY(), new Cash.Commands.Move());
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 8

    // DOCSTART 9
    @Test
    public void chainCommercialPaperDoubleSpend() {
        PartyAndReference issuer = getMEGA_CORP().ref(defaultRef);
        ledger(l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), getALICE()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(Cash.PROGRAM_ID, "paper", getPaper());
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(getTEST_TX_TIME());
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), getMEGA_CORP()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(getALICE()));
                tx.command(getALICE_PUBKEY(), new Cash.Commands.Move());
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.transaction(tx -> {
                tx.input("paper");
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                // We moved a paper to other pubkey.
                tx.output(JCP_PROGRAM_ID, "bob's paper", inputPaper.withOwner(getBOB()));
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });
            l.fails();
            return Unit.INSTANCE;
        });
    }
    // DOCEND 9

    // DOCSTART 10
    @Test
    public void chainCommercialPaperTweak() {
        PartyAndReference issuer = getMEGA_CORP().ref(defaultRef);
        ledger(l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), getALICE()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(Cash.PROGRAM_ID, "paper", getPaper());
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(getTEST_TX_TIME());
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), getMEGA_CORP()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(getALICE()));
                tx.command(getALICE_PUBKEY(), new Cash.Commands.Move());
                tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.tweak(lw -> {
                lw.transaction(tx -> {
                    tx.input("paper");
                    JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                    // We moved a paper to another pubkey.
                    tx.output(JCP_PROGRAM_ID, "bob's paper", inputPaper.withOwner(getBOB()));
                    tx.command(getMEGA_CORP_PUBKEY(), new JavaCommercialPaper.Commands.Move());
                    return tx.verifies();
                });
                lw.fails();
                return Unit.INSTANCE;
            });
            l.verifies();
            return Unit.INSTANCE;
        });
    }
    // DOCEND 10
}