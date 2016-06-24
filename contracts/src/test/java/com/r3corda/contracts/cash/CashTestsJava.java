package com.r3corda.contracts.cash;

import com.r3corda.core.contracts.PartyAndReference;
import com.r3corda.core.serialization.OpaqueBytes;
import org.junit.Test;

import static com.r3corda.core.testing.JavaTestHelpers.*;
import static com.r3corda.core.contracts.JavaTestHelpers.*;
import static com.r3corda.contracts.testing.JavaTestHelpers.*;

/**
 * This is an incomplete Java replica of CashTests.kt to show how to use the Java test DSL
 */
public class CashTestsJava {

    private OpaqueBytes defaultRef = new OpaqueBytes(new byte[]{1});;
    private PartyAndReference defaultIssuer = MEGA_CORP.ref(defaultRef);
    private Cash.State inState = new Cash.State(issuedBy(DOLLARS(1000), defaultIssuer), DUMMY_PUBKEY_1);
    private Cash.State outState = new Cash.State(inState.getAmount(), DUMMY_PUBKEY_2);;

    @Test
    public void trivial() {

        transaction(tx -> {
            tx.input(inState);
            tx.failsRequirement("the amounts balance");

            tx.tweak(tw -> {
                tw.output(new Cash.State(issuedBy(DOLLARS(2000), defaultIssuer), DUMMY_PUBKEY_2));
                return tw.failsRequirement("the amounts balance");
            });

            tx.tweak(tw -> {
                tw.output(outState);
                // No command arguments
                return tw.failsRequirement("required com.r3corda.contracts.cash.FungibleAsset.Commands.Move command");
            });
            tx.tweak(tw -> {
                tw.output(outState);
                tw.arg(DUMMY_PUBKEY_2, new Cash.Commands.Move());
                return tw.failsRequirement("the owning keys are the same as the signing keys");
            });
            tx.tweak(tw -> {
                tw.output(outState);
                tw.output(issuedBy(outState, MINI_CORP));
                tw.arg(DUMMY_PUBKEY_1, new Cash.Commands.Move());
                return tw.failsRequirement("at least one asset input");
            });

            // Simple reallocation works.
            return tx.tweak(tw -> {
                tw.output(outState);
                tw.arg(DUMMY_PUBKEY_1, new Cash.Commands.Move());
                return tw.accepts();
            });
        });
    }
}
