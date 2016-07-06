package com.r3corda.contracts.asset;

import com.r3corda.core.contracts.PartyAndReference;
import com.r3corda.core.serialization.OpaqueBytes;
import kotlin.Unit;
import org.junit.Test;

import static com.r3corda.core.testing.JavaTestHelpers.*;
import static com.r3corda.core.contracts.JavaTestHelpers.*;
import static com.r3corda.contracts.testing.JavaTestHelpers.*;

/**
 * This is an incomplete Java replica of CashTests.kt to show how to use the Java test DSL
 */
public class CashTestsJava {

    private OpaqueBytes defaultRef = new OpaqueBytes(new byte[]{1});;
    private PartyAndReference defaultIssuer = getMEGA_CORP().ref(defaultRef);
    private Cash.State inState = new Cash.State(issuedBy(DOLLARS(1000), defaultIssuer), getDUMMY_PUBKEY_1());
    private Cash.State outState = new Cash.State(inState.getAmount(), getDUMMY_PUBKEY_2());

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
                    return tw.failsWith("required com.r3corda.contracts.asset.FungibleAsset.Commands.Move command");
                });
                tx.tweak(tw -> {
                    tw.output(outState);
                    tw.command(getDUMMY_PUBKEY_2(), new Cash.Commands.Move());
                    return tw.failsWith("the owning keys are the same as the signing keys");
                });
                tx.tweak(tw -> {
                    tw.output(outState);
                    tw.output(issuedBy(outState, getMINI_CORP()));
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
