package net.corda.finance.contracts.asset;

import net.corda.core.contracts.PartyAndReference;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.testing.DummyCommandData;
import org.junit.Test;

import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.finance.Currencies.issuedBy;
import static net.corda.testing.CoreTestUtils.*;
import static net.corda.testing.NodeTestUtils.transaction;

/**
 * This is an incomplete Java replica of CashTests.kt to show how to use the Java test DSL
 */
public class CashTestsJava {
    private final OpaqueBytes defaultRef = new OpaqueBytes(new byte[]{1});
    private final PartyAndReference defaultIssuer = getMEGA_CORP().ref(defaultRef);
    private final Cash.State inState = new Cash.State(issuedBy(DOLLARS(1000), defaultIssuer), new AnonymousParty(getMEGA_CORP_PUBKEY()));
    private final Cash.State outState = new Cash.State(inState.getAmount(), new AnonymousParty(getMINI_CORP_PUBKEY()));

    @Test
    public void trivial() {
        transaction(tx -> {
            tx.attachment(Cash.PROGRAM_ID);

            tx.input(Cash.PROGRAM_ID, inState);

            tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, () -> new Cash.State(issuedBy(DOLLARS(2000), defaultIssuer), new AnonymousParty(getMINI_CORP_PUBKEY())));
                tw.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                return tw.failsWith("the amounts balance");
            });

            tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, () -> outState);
                tw.command(getMEGA_CORP_PUBKEY(), DummyCommandData.INSTANCE);
                // Invalid command
                return tw.failsWith("required net.corda.finance.contracts.asset.Cash.Commands.Move command");
            });
            tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, () -> outState);
                tw.command(getMINI_CORP_PUBKEY(), new Cash.Commands.Move());
                return tw.failsWith("the owning keys are a subset of the signing keys");
            });
            tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, () -> outState);
                // issuedBy() can't be directly imported because it conflicts with other identically named functions
                // with different overloads (for some reason).
                tw.output(Cash.PROGRAM_ID, () -> outState.issuedBy(getMINI_CORP()));
                tw.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                return tw.failsWith("at least one cash input");
            });

            // Simple reallocation works.
            return tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, () -> outState);
                tw.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                return tw.verifies();
            });
        });
    }
}
