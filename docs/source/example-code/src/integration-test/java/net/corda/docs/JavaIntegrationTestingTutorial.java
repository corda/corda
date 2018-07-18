package net.corda.docs;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Issued;
import net.corda.core.contracts.Structures;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.Vault;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.finance.contracts.asset.Cash;
import net.corda.finance.flows.CashIssueAndPaymentFlow;
import net.corda.finance.flows.CashPaymentFlow;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import net.corda.testing.node.User;
import org.junit.Test;
import rx.Observable;

import java.util.Currency;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.node.services.Permissions.invokeRpc;
import static net.corda.node.services.Permissions.startFlow;
import static net.corda.testing.core.ExpectKt.expect;
import static net.corda.testing.core.ExpectKt.expectEvents;
import static net.corda.testing.core.TestConstants.ALICE_NAME;
import static net.corda.testing.core.TestConstants.BOB_NAME;
import static net.corda.testing.driver.Driver.driver;
import static org.junit.Assert.assertEquals;

public class JavaIntegrationTestingTutorial {
    @Test
    public void aliceBobCashExchangeExample() {
        // START 1
        driver(new DriverParameters().withExtraCordappPackagesToScan(singletonList("net.corda.finance.contracts.asset")), dsl -> {
            User aliceUser = new User("aliceUser", "testPassword1", new HashSet<>(asList(
                    startFlow(CashIssueAndPaymentFlow.class),
                    invokeRpc("vaultTrack")
            )));

            User bobUser = new User("bobUser", "testPassword2", new HashSet<>(asList(
                    startFlow(CashPaymentFlow.class),
                    invokeRpc("vaultTrack")
            )));

            try {
                List<CordaFuture<NodeHandle>> nodeHandleFutures = asList(
                        dsl.startNode(new NodeParameters().withProvidedName(ALICE_NAME).withRpcUsers(singletonList(aliceUser))),
                        dsl.startNode(new NodeParameters().withProvidedName(BOB_NAME).withRpcUsers(singletonList(bobUser)))
                );

                NodeHandle alice = nodeHandleFutures.get(0).get();
                NodeHandle bob = nodeHandleFutures.get(1).get();
                // END 1

                // START 2
                CordaRPCClient aliceClient = new CordaRPCClient(alice.getRpcAddress());
                CordaRPCOps aliceProxy = aliceClient.start("aliceUser", "testPassword1").getProxy();

                CordaRPCClient bobClient = new CordaRPCClient(bob.getRpcAddress());
                CordaRPCOps bobProxy = bobClient.start("bobUser", "testPassword2").getProxy();
                // END 2

                // START 3
                Observable<Vault.Update<Cash.State>> bobVaultUpdates = bobProxy.vaultTrack(Cash.State.class).getUpdates();
                Observable<Vault.Update<Cash.State>> aliceVaultUpdates = aliceProxy.vaultTrack(Cash.State.class).getUpdates();
                // END 3

                // START 4
                OpaqueBytes issueRef = OpaqueBytes.of((byte)0);
                aliceProxy.startFlowDynamic(
                        CashIssueAndPaymentFlow.class,
                        DOLLARS(1000),
                        issueRef,
                        bob.getNodeInfo().getLegalIdentities().get(0),
                        true,
                        dsl.getDefaultNotaryIdentity()
                ).getReturnValue().get();

                @SuppressWarnings("unchecked")
                Class<Vault.Update<Cash.State>> cashVaultUpdateClass = (Class<Vault.Update<Cash.State>>)(Class<?>)Vault.Update.class;

                expectEvents(bobVaultUpdates, true, () ->
                        expect(cashVaultUpdateClass, update -> true, update -> {
                            System.out.println("Bob got vault update of " + update);
                            Amount<Issued<Currency>> amount = update.getProduced().iterator().next().getState().getData().getAmount();
                            assertEquals(DOLLARS(1000), Structures.withoutIssuer(amount));
                            return null;
                        })
                );
                // END 4

                // START 5
                bobProxy.startFlowDynamic(
                        CashPaymentFlow.class,
                        DOLLARS(1000),
                        alice.getNodeInfo().getLegalIdentities().get(0)
                ).getReturnValue().get();

                expectEvents(aliceVaultUpdates, true, () ->
                        expect(cashVaultUpdateClass, update -> true, update -> {
                            System.out.println("Alice got vault update of " + update);
                            Amount<Issued<Currency>> amount = update.getProduced().iterator().next().getState().getData().getAmount();
                            assertEquals(DOLLARS(1000), Structures.withoutIssuer(amount));
                            return null;
                        })
                );
                // END 5
            } catch (Exception e) {
                throw new RuntimeException("Exception thrown in driver DSL", e);
            }
            return null;
        });
    }
}
