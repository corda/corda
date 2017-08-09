package net.corda.finance.contracts.asset;

import kotlin.Unit;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.testing.DummyCommandData;
import org.junit.Test;

import static net.corda.finance.CurrencyUtils.DOLLARS;
import static net.corda.finance.CurrencyUtils.issuedBy;
import static net.corda.testing.CoreTestUtils.*;

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
        ledger(lg -> {
            lg.transaction(tx -> {
                tx.input(inState);

                tx.tweak(tw -> {
                    tw.output(new Cash.State(issuedBy(DOLLARS(2000), defaultIssuer), new AnonymousParty(getMINI_CORP_PUBKEY())));
                    tw.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tw.failsWith("the amounts balance");
                });

                tx.tweak(tw -> {
                    tw.output(outState);
                    tw.command(getMEGA_CORP_PUBKEY(), DummyCommandData.INSTANCE);
                    // Invalid command
                    return tw.failsWith("required net.corda.finance.contracts.asset.Cash.Commands.Move command");
                });
                tx.tweak(tw -> {
                    tw.output(outState);
                    tw.command(getMINI_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tw.failsWith("the owning keys are a subset of the signing keys");
                });
                tx.tweak(tw -> {
                    tw.output(outState);
                    // issuedBy() can't be directly imported because it conflicts with other identically named functions
                    // with different overloads (for some reason).
                    tw.output(outState.issuedBy(getMINI_CORP()));
                    tw.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tw.failsWith("at least one cash input");
                });

                // Simple reallocation works.
                return tx.tweak(tw -> {
                    tw.output(outState);
                    tw.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tw.verifies();
                });
            });
            return Unit.INSTANCE;
        });
    }
}
