package net.corda.docs.java.tutorial.testdsl;

import kotlin.Unit;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.finance.contracts.ICommercialPaperState;
import net.corda.finance.contracts.JavaCommercialPaper;
import net.corda.finance.contracts.asset.Cash;
import net.corda.node.services.api.IdentityServiceInternal;
import net.corda.testing.SerializationEnvironmentRule;
import net.corda.testing.node.MockServices;
import net.corda.testing.TestIdentity;
import org.junit.Rule;
import org.junit.Test;

import java.security.PublicKey;
import java.time.temporal.ChronoUnit;

import static net.corda.core.crypto.Crypto.generateKeyPair;
import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.finance.Currencies.issuedBy;
import static net.corda.finance.contracts.JavaCommercialPaper.JCP_PROGRAM_ID;
import static net.corda.testing.node.NodeTestUtils.ledger;
import static net.corda.testing.node.NodeTestUtils.transaction;
import static net.corda.testing.CoreTestUtils.rigorousMock;
import static net.corda.testing.TestConstants.*;
import static org.mockito.Mockito.doReturn;

public class CommercialPaperTest {
    private static final TestIdentity ALICE = new TestIdentity(getALICE_NAME(), 70L);
    private static final PublicKey BIG_CORP_PUBKEY = generateKeyPair().getPublic();
    private static final TestIdentity BOB = new TestIdentity(getBOB_NAME(), 80L);
    private static final TestIdentity MEGA_CORP = new TestIdentity(new CordaX500Name("MegaCorp", "London", "GB"));
    private static final Party DUMMY_NOTARY = new TestIdentity(getDUMMY_NOTARY_NAME(), 20L).getParty();
    @Rule
    public final SerializationEnvironmentRule testSerialization = new SerializationEnvironmentRule();
    private final byte[] defaultRef = {123};
    private final MockServices ledgerServices;

    {
        IdentityServiceInternal identityService = rigorousMock(IdentityServiceInternal.class);
        doReturn(MEGA_CORP.getParty()).when(identityService).partyFromKey(MEGA_CORP.getPublicKey());
        doReturn(null).when(identityService).partyFromKey(BIG_CORP_PUBKEY);
        doReturn(null).when(identityService).partyFromKey(ALICE.getPublicKey());
        ledgerServices = new MockServices(identityService, MEGA_CORP.getName());
    }

    // DOCSTART 1
    private ICommercialPaperState getPaper() {
        return new JavaCommercialPaper.State(
                MEGA_CORP.ref(defaultRef),
                MEGA_CORP.getParty(),
                issuedBy(DOLLARS(1000), MEGA_CORP.ref(defaultRef)),
                getTEST_TX_TIME().plus(7, ChronoUnit.DAYS)
        );
    }
    // DOCEND 1

    // DOCSTART 2
    @Test
    public void simpleCP() {
        ICommercialPaperState inState = getPaper();
        ledger(ledgerServices, DUMMY_NOTARY, l -> {
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
        ledger(ledgerServices, DUMMY_NOTARY, l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Move());
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
        ledger(ledgerServices, DUMMY_NOTARY, l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Move());
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
        ledger(ledgerServices, DUMMY_NOTARY, l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                tx.attachments(JCP_PROGRAM_ID);
                tx.failsWith("the state is propagated");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inState.withOwner(ALICE.getParty()));
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 5

    // DOCSTART 6
    @Test
    public void simpleIssuanceWithTweak() {
        ledger(ledgerServices, DUMMY_NOTARY, l -> {
            l.transaction(tx -> {
                tx.output(JCP_PROGRAM_ID, "paper", getPaper()); // Some CP is issued onto the ledger by MegaCorp.
                tx.attachments(JCP_PROGRAM_ID);
                tx.tweak(tw -> {
                    tw.command(BIG_CORP_PUBKEY, new JavaCommercialPaper.Commands.Issue());
                    tw.timeWindow(getTEST_TX_TIME());
                    return tw.failsWith("output states are issued by a command signer");
                });
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
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
        transaction(ledgerServices, DUMMY_NOTARY, tx -> {
            tx.output(JCP_PROGRAM_ID, "paper", getPaper()); // Some CP is issued onto the ledger by MegaCorp.
            tx.attachments(JCP_PROGRAM_ID);
            tx.tweak(tw -> {
                tw.command(BIG_CORP_PUBKEY, new JavaCommercialPaper.Commands.Issue());
                tw.timeWindow(getTEST_TX_TIME());
                return tw.failsWith("output states are issued by a command signer");
            });
            tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
            tx.timeWindow(getTEST_TX_TIME());
            return tx.verifies();
        });
    }
    // DOCEND 7

    // DOCSTART 8
    @Test
    public void chainCommercialPaper() {
        PartyAndReference issuer = MEGA_CORP.ref(defaultRef);
        ledger(ledgerServices, DUMMY_NOTARY, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), ALICE.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(JCP_PROGRAM_ID, "paper", getPaper());
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(getTEST_TX_TIME());
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), MEGA_CORP.getParty()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(ALICE.getParty()));
                tx.command(ALICE.getPublicKey(), new Cash.Commands.Move());
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 8

    // DOCSTART 9
    @Test
    public void chainCommercialPaperDoubleSpend() {
        PartyAndReference issuer = MEGA_CORP.ref(defaultRef);
        ledger(ledgerServices, DUMMY_NOTARY, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), ALICE.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(Cash.PROGRAM_ID, "paper", getPaper());
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(getTEST_TX_TIME());
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), MEGA_CORP.getParty()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(ALICE.getParty()));
                tx.command(ALICE.getPublicKey(), new Cash.Commands.Move());
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.transaction(tx -> {
                tx.input("paper");
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                // We moved a paper to other pubkey.
                tx.output(JCP_PROGRAM_ID, "bob's paper", inputPaper.withOwner(BOB.getParty()));
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Move());
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
        PartyAndReference issuer = MEGA_CORP.ref(defaultRef);
        ledger(ledgerServices, DUMMY_NOTARY, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), ALICE.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(Cash.PROGRAM_ID, "paper", getPaper());
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(getTEST_TX_TIME());
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), MEGA_CORP.getParty()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(ALICE.getParty()));
                tx.command(ALICE.getPublicKey(), new Cash.Commands.Move());
                tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.tweak(lw -> {
                lw.transaction(tx -> {
                    tx.input("paper");
                    JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                    // We moved a paper to another pubkey.
                    tx.output(JCP_PROGRAM_ID, "bob's paper", inputPaper.withOwner(BOB.getParty()));
                    tx.command(MEGA_CORP.getPublicKey(), new JavaCommercialPaper.Commands.Move());
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