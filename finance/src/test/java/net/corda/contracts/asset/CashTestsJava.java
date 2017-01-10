package net.corda.contracts.asset;

import kotlin.*;
import net.corda.core.contracts.*;
import net.corda.core.serialization.*;
import org.junit.*;

import static net.corda.core.contracts.ContractsDSL.*;
import static net.corda.core.utilities.TestConstants.*;
import static net.corda.testing.CoreTestUtils.*;

/**
 * This is an incomplete Java replica of CashTests.kt to show how to use the Java test DSL
 */
public class CashTestsJava {
    private final OpaqueBytes defaultRef = new OpaqueBytes(new byte[]{1});
    private final PartyAndReference defaultIssuer = getMEGA_CORP().ref(defaultRef);
    private final Cash.State inState = new Cash.State(issuedBy(DOLLARS(1000), defaultIssuer), getDUMMY_PUBKEY_1());
    private final Cash.State outState = new Cash.State(inState.getAmount(), getDUMMY_PUBKEY_2());

    @Test
    public void trivial() {
        ledger(lg -> {
            lg.transaction(tx -> {
                tx.input(inState);
                tx.failsWith("the amounts balance");

                tx.tweak(tw -> {
                    tw.output(new Cash.State(issuedBy(DOLLARS(2000), defaultIssuer), getDUMMY_PUBKEY_2()));
                    return tw.failsWith("the amounts balance");
                });

                tx.tweak(tw -> {
                    tw.output(outState);
                    // No command arguments
                    return tw.failsWith("required net.corda.core.contracts.FungibleAsset.Commands.Move command");
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
                    return tw.failsWith("at least one asset input");
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
