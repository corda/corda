package net.corda.docs.java.tutorial.testdsl;

import kotlin.Unit;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.contracts.TransactionVerificationException;
import net.corda.core.identity.CordaX500Name;
import net.corda.finance.contracts.ICommercialPaperState;
import net.corda.finance.contracts.JavaCommercialPaper;
import net.corda.finance.contracts.asset.Cash;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static java.util.Collections.singletonList;
import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.finance.Currencies.issuedBy;
import static net.corda.finance.contracts.JavaCommercialPaper.JCP_PROGRAM_ID;
import static net.corda.testing.core.TestConstants.ALICE_NAME;
import static net.corda.testing.core.TestConstants.BOB_NAME;
import static net.corda.testing.node.MockServicesKt.makeTestIdentityService;
import static net.corda.testing.node.NodeTestUtils.ledger;
import static net.corda.testing.node.NodeTestUtils.transaction;

public class CommercialPaperTest {
    private static final TestIdentity alice = new TestIdentity(ALICE_NAME, 70L);
    // DOCSTART 14
    private static final TestIdentity bigCorp = new TestIdentity(new CordaX500Name("BigCorp", "New York", "GB"));
    // DOCEND 14
    private static final TestIdentity bob = new TestIdentity(BOB_NAME, 80L);
    private static final TestIdentity megaCorp = new TestIdentity(new CordaX500Name("MegaCorp", "London", "GB"));
    private final byte[] defaultRef = {123};
    private static final Instant TEST_TX_TIME = Instant.parse("2015-04-17T12:00:00.00Z");
    private MockServices ledgerServices;

    @Before
    public void setUp() {
        // DOCSTART 11
        ledgerServices = new MockServices(
                // A list of packages to scan for cordapps
                singletonList("net.corda.finance.contracts"),
                // The identity represented by this set of mock services. Defaults to a test identity.
                // You can also use the alternative parameter initialIdentityName which accepts a
                // [CordaX500Name]
                megaCorp,
                // An implementation of [IdentityService], which contains a list of all identities known
                // to the node. Use [makeTestIdentityService] which returns an implementation of
                // [InMemoryIdentityService] with the given identities
                makeTestIdentityService(megaCorp.getIdentity())
        );
        // DOCEND 11
    }

    @SuppressWarnings("unused")
    // DOCSTART 12
    private final MockServices simpleLedgerServices = new MockServices(
            // This is the identity of the node
            megaCorp,
            // Other identities the test node knows about
            bigCorp,
            alice
    );
    // DOCEND 12

    // DOCSTART 1
    private ICommercialPaperState getPaper() {
        return new JavaCommercialPaper.State(
                megaCorp.ref(defaultRef),
                megaCorp.getParty(),
                issuedBy(DOLLARS(1000), megaCorp.ref(defaultRef)),
                TEST_TX_TIME.plus(7, ChronoUnit.DAYS)
        );
    }
    // DOCEND 1

    // DOCSTART 2
    // This example test will fail with this exception.
    @Test(expected = IllegalStateException.class)
    public void simpleCP() {
        ICommercialPaperState inState = getPaper();
        ledger(ledgerServices, l -> {
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
    // This example test will fail with this exception.
    @Test(expected = TransactionVerificationException.ContractRejection.class)
    public void simpleCPMove() {
        ICommercialPaperState inState = getPaper();
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
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
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                tx.attachments(JCP_PROGRAM_ID);
                return tx.failsWith("the state is propagated");
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 4

    // DOCSTART 5
    @Test
    public void simpleCPMoveSuccessAndFailure() {
        ICommercialPaperState inState = getPaper();
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                tx.attachments(JCP_PROGRAM_ID);
                tx.failsWith("the state is propagated");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inState.withOwner(alice.getParty()));
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 5

    // DOCSTART 13
    @Test
    public void simpleCPMoveSuccess() {
        ICommercialPaperState inState = getPaper();
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.input(JCP_PROGRAM_ID, inState);
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(TEST_TX_TIME);
                tx.output(JCP_PROGRAM_ID, "alice's paper", inState.withOwner(alice.getParty()));
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 13

    // DOCSTART 6
    @Test
    public void simpleIssuanceWithTweak() {
        ledger(ledgerServices, l -> {
            l.transaction(tx -> {
                tx.output(JCP_PROGRAM_ID, "paper", getPaper()); // Some CP is issued onto the ledger by MegaCorp.
                tx.attachments(JCP_PROGRAM_ID);
                tx.tweak(tw -> {
                    tw.command(bigCorp.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                    tw.timeWindow(TEST_TX_TIME);
                    return tw.failsWith("output states are issued by a command signer");
                });
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                tx.timeWindow(TEST_TX_TIME);
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 6

    // DOCSTART 7
    @Test
    public void simpleIssuanceWithTweakTopLevelTx() {
        transaction(ledgerServices, tx -> {
            tx.output(JCP_PROGRAM_ID, "paper", getPaper()); // Some CP is issued onto the ledger by MegaCorp.
            tx.attachments(JCP_PROGRAM_ID);
            tx.tweak(tw -> {
                tw.command(bigCorp.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                tw.timeWindow(TEST_TX_TIME);
                return tw.failsWith("output states are issued by a command signer");
            });
            tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
            tx.timeWindow(TEST_TX_TIME);
            return tx.verifies();
        });
    }
    // DOCEND 7

    // DOCSTART 8
    @Test
    public void chainCommercialPaper() {
        PartyAndReference issuer = megaCorp.ref(defaultRef);
        ledger(ledgerServices, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), alice.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(JCP_PROGRAM_ID, "paper", getPaper());
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(TEST_TX_TIME);
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), megaCorp.getParty()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(alice.getParty()));
                tx.command(alice.getPublicKey(), new Cash.Commands.Move());
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });
            return Unit.INSTANCE;
        });
    }
    // DOCEND 8

    // DOCSTART 9
    @Test
    public void chainCommercialPaperDoubleSpend() {
        PartyAndReference issuer = megaCorp.ref(defaultRef);
        ledger(ledgerServices, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), alice.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(JCP_PROGRAM_ID, "paper", getPaper());
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(TEST_TX_TIME);
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), megaCorp.getParty()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(alice.getParty()));
                tx.command(alice.getPublicKey(), new Cash.Commands.Move());
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.transaction(tx -> {
                tx.input("paper");
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                // We moved a paper to other pubkey.
                tx.output(JCP_PROGRAM_ID, "bob's paper", inputPaper.withOwner(bob.getParty()));
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
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
        PartyAndReference issuer = megaCorp.ref(defaultRef);
        ledger(ledgerServices, l -> {
            l.unverifiedTransaction(tx -> {
                tx.output(Cash.PROGRAM_ID, "alice's $900",
                        new Cash.State(issuedBy(DOLLARS(900), issuer), alice.getParty()));
                tx.attachments(Cash.PROGRAM_ID);
                return Unit.INSTANCE;
            });

            // Some CP is issued onto the ledger by MegaCorp.
            l.transaction("Issuance", tx -> {
                tx.output(JCP_PROGRAM_ID, "paper", getPaper());
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Issue());
                tx.attachments(JCP_PROGRAM_ID);
                tx.timeWindow(TEST_TX_TIME);
                return tx.verifies();
            });

            l.transaction("Trade", tx -> {
                tx.input("paper");
                tx.input("alice's $900");
                tx.output(Cash.PROGRAM_ID, "borrowed $900", new Cash.State(issuedBy(DOLLARS(900), issuer), megaCorp.getParty()));
                JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                tx.output(JCP_PROGRAM_ID, "alice's paper", inputPaper.withOwner(alice.getParty()));
                tx.command(alice.getPublicKey(), new Cash.Commands.Move(JavaCommercialPaper.class));
                tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
                return tx.verifies();
            });

            l.tweak(lw -> {
                lw.transaction(tx -> {
                    tx.input("paper");
                    JavaCommercialPaper.State inputPaper = l.retrieveOutput(JavaCommercialPaper.State.class, "paper");
                    // We moved a paper to another pubkey.
                    tx.output(JCP_PROGRAM_ID, "bob's paper", inputPaper.withOwner(bob.getParty()));
                    tx.command(megaCorp.getPublicKey(), new JavaCommercialPaper.Commands.Move());
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