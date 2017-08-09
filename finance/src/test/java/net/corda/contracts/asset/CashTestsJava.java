package net.corda.contracts.asset;

import kotlin.Unit;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.utilities.OpaqueBytes;
import org.junit.Test;

import static net.corda.core.contracts.ContractsDSL.DOLLARS;
import static net.corda.core.contracts.ContractsDSL.issuedBy;
import static net.corda.testing.TestConstants.getDUMMY_PUBKEY_1;
import static net.corda.testing.TestConstants.getDUMMY_PUBKEY_2;
import static net.corda.testing.CoreTestUtils.*;

/**
 * This is an incomplete Java replica of CashTests.kt to show how to use the Java test DSL
 */
public class CashTestsJava {
    private final OpaqueBytes defaultRef = new OpaqueBytes(new byte[]{1});
    private final PartyAndReference defaultIssuer = getMEGA_CORP().ref(defaultRef);
    private final Cash.State inState = new Cash.State(issuedBy(DOLLARS(1000), defaultIssuer), new AnonymousParty(getDUMMY_PUBKEY_1()));
    private final Cash.State outState = new Cash.State(inState.getAmount(), new AnonymousParty(getDUMMY_PUBKEY_2()));

    @Test
    public void trivial() {
        ledger(lg -> {
            lg.transaction(tx -> {
                tx.input(inState);
                tx.failsWith("the amounts balance");

                tx.tweak(tw -> {
                    tw.output(new Cash.State(issuedBy(DOLLARS(2000), defaultIssuer), new AnonymousParty(getDUMMY_PUBKEY_2())));
                    return tw.failsWith("the amounts balance");
                });

                tx.tweak(tw -> {
                    tw.output(outState);
                    // No command arguments
                    return tw.failsWith("required net.corda.contracts.asset.Cash.Commands.Move command");
                });
                tx.tweak(tw -> {
                    tw.output(outState);
                    tw.command(getDUMMY_PUBKEY_2(), new Cash.Commands.Move());
                    return tw.failsWith("the owning keys are a subset of the signing keys");
                });
                tx.tweak(tw -> {
                    tw.output(outState);
                    // issuedBy() can't be directly imported because it conflicts with other identically named functions
                    // with different overloads (for some reason).
                    tw.output(CashKt.issuedBy(outState, getMINI_CORP()));
                    tw.command(getDUMMY_PUBKEY_1(), new Cash.Commands.Move());
                    return tw.failsWith("at least one cash input");
                });

                // Simple reallocation works.
                return tx.tweak(tw -> {
                    tw.output(outState);
                    tw.command(getDUMMY_PUBKEY_1(), new Cash.Commands.Move());
                    return tw.verifies();
                });
            });
            return Unit.INSTANCE;
        });
    }
}
