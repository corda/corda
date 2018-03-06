/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts.asset;

import net.corda.core.contracts.PartyAndReference;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.node.services.api.IdentityServiceInternal;
import net.corda.testing.core.DummyCommandData;
import net.corda.testing.core.SerializationEnvironmentRule;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Rule;
import org.junit.Test;

import static java.util.Collections.emptyList;
import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.finance.Currencies.issuedBy;
import static net.corda.testing.node.NodeTestUtils.transaction;
import static net.corda.testing.internal.InternalTestUtilsKt.rigorousMock;
import static net.corda.testing.core.TestConstants.DUMMY_NOTARY_NAME;
import static org.mockito.Mockito.doReturn;

/**
 * This is an incomplete Java replica of CashTests.kt to show how to use the Java test DSL
 */
public class CashTestsJava {
    private static final Party DUMMY_NOTARY = new TestIdentity(DUMMY_NOTARY_NAME, 20L).getParty();
    private static final TestIdentity MEGA_CORP = new TestIdentity(new CordaX500Name("MegaCorp", "London", "GB"));
    private static final TestIdentity MINI_CORP = new TestIdentity(new CordaX500Name("MiniCorp", "London", "GB"));
    private final PartyAndReference defaultIssuer = MEGA_CORP.ref((byte) 1);
    private final Cash.State inState = new Cash.State(issuedBy(DOLLARS(1000), defaultIssuer), new AnonymousParty(MEGA_CORP.getPublicKey()));
    private final Cash.State outState = new Cash.State(inState.getAmount(), new AnonymousParty(MINI_CORP.getPublicKey()));
    @Rule
    public final SerializationEnvironmentRule testSerialization = new SerializationEnvironmentRule();

    @Test
    public void trivial() {
        IdentityServiceInternal identityService = rigorousMock(IdentityServiceInternal.class);
        doReturn(MEGA_CORP.getParty()).when(identityService).partyFromKey(MEGA_CORP.getPublicKey());
        doReturn(MINI_CORP.getParty()).when(identityService).partyFromKey(MINI_CORP.getPublicKey());
        transaction(new MockServices(emptyList(), MEGA_CORP.getName(), identityService), DUMMY_NOTARY, tx -> {
            tx.attachment(Cash.PROGRAM_ID);

            tx.input(Cash.PROGRAM_ID, inState);

            tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, new Cash.State(issuedBy(DOLLARS(2000), defaultIssuer), new AnonymousParty(MINI_CORP.getPublicKey())));
                tw.command(MEGA_CORP.getPublicKey(), new Cash.Commands.Move());
                return tw.failsWith("the amounts balance");
            });

            tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, outState);
                tw.command(MEGA_CORP.getPublicKey(), DummyCommandData.INSTANCE);
                // Invalid command
                return tw.failsWith("required net.corda.finance.contracts.asset.Cash.Commands.Move command");
            });
            tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, outState);
                tw.command(MINI_CORP.getPublicKey(), new Cash.Commands.Move());
                return tw.failsWith("the owning keys are a subset of the signing keys");
            });
            tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, outState);
                // issuedBy() can't be directly imported because it conflicts with other identically named functions
                // with different overloads (for some reason).
                tw.output(Cash.PROGRAM_ID, outState.issuedBy(MINI_CORP.getParty()));
                tw.command(MEGA_CORP.getPublicKey(), new Cash.Commands.Move());
                return tw.failsWith("at least one cash input");
            });

            // Simple reallocation works.
            return tx.tweak(tw -> {
                tw.output(Cash.PROGRAM_ID, outState);
                tw.command(MEGA_CORP.getPublicKey(), new Cash.Commands.Move());
                return tw.verifies();
            });
        });
    }
}
