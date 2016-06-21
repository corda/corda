package com.r3corda.contracts.cash;

import com.r3corda.core.contracts.PartyAndReference;
import com.r3corda.core.serialization.OpaqueBytes;
import com.r3corda.core.testing.TransactionTestBase;
import org.junit.Test;

import static com.r3corda.core.testing.Dummies.*;
import static com.r3corda.contracts.testing.Methods.*;
import static com.r3corda.core.contracts.Currencies.*;
import static com.r3corda.core.contracts.Methods.*;

public class CashTestsJava extends TransactionTestBase {

    private OpaqueBytes defaultRef = new OpaqueBytes(new byte[]{1});;
    private PartyAndReference defaultIssuer = MEGA_CORP.ref(defaultRef);
    private Cash.State inState = new Cash.State(issued_by(DOLLARS(2000), defaultIssuer), DUMMY_PUBKEY_1);
    private Cash.State outState = inState.copy(inState.getAmount(), DUMMY_PUBKEY_2);;

    @Test
    public void trivial() {
        transaction(begin
                .input(inState)
                .fails_requirement("the amounts balance")

                .tweak(begin
                        .output(outState.copy(issued_by(DOLLARS(2000), defaultIssuer), DUMMY_PUBKEY_2))
                        .fails_requirement("the amounts balance")
                )

                .tweak(begin
                        .output(outState)
                        .fails_requirement("required com.r3corda.contracts.cash.FungibleAsset.Commands.Move command")
                )

                .tweak(begin
                        .output(outState)
                        .arg(DUMMY_PUBKEY_2, new Cash.Commands.Move())
                        .fails_requirement("the owning keys are the same as the signing keys")
                )

                .tweak(begin
                        .output(outState)
                        .output(issued_by(outState, MINI_CORP))
                        .arg(DUMMY_PUBKEY_1, new Cash.Commands.Move())
                        .fails_requirement("at least one asset input")
                )

                .tweak(begin
                        .output(outState)
                        .arg(DUMMY_PUBKEY_1, new Cash.Commands.Move())
                        .accepts()
                )
        );
    }
}
