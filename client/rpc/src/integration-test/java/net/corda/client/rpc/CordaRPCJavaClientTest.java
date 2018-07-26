/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.rpc;

import net.corda.core.contracts.Amount;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.finance.flows.AbstractCashFlow;
import net.corda.finance.flows.CashIssueFlow;
import net.corda.finance.flows.CashPaymentFlow;
import net.corda.finance.schemas.CashSchemaV1;
import net.corda.node.internal.StartedNode;
import net.corda.node.internal.StartedNodeWithInternals;
import net.corda.testing.internal.InternalTestUtilsKt;
import net.corda.testing.node.User;
import net.corda.testing.internal.IntegrationTestKt;
import net.corda.testing.internal.IntegrationTestSchemas;
import net.corda.testing.node.internal.NodeBasedTest;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static kotlin.test.AssertionsKt.assertEquals;
import static net.corda.finance.Currencies.DOLLARS;
import static net.corda.finance.contracts.GetBalances.getCashBalance;
import static net.corda.node.services.Permissions.invokeRpc;
import static net.corda.node.services.Permissions.startFlow;
import static net.corda.testing.core.TestConstants.ALICE_NAME;

public class CordaRPCJavaClientTest extends NodeBasedTest {
    public CordaRPCJavaClientTest() {
        super(Arrays.asList("net.corda.finance.contracts", CashSchemaV1.class.getPackage().getName()));
    }

    @ClassRule
    public static IntegrationTestSchemas databaseSchemas = new IntegrationTestSchemas(IntegrationTestKt.toDatabaseSchemaName(ALICE_NAME));

    private List<String> perms = Arrays.asList(
            startFlow(CashPaymentFlow.class),
            startFlow(CashIssueFlow.class),
            invokeRpc("nodeInfo"),
            invokeRpc("vaultQueryBy"),
            invokeRpc("vaultQueryByCriteria"));
    private Set<String> permSet = new HashSet<>(perms);
    private User rpcUser = new User("user1", "test", permSet);

    private StartedNodeWithInternals node;
    private CordaRPCClient client;
    private RPCConnection<CordaRPCOps> connection = null;
    private CordaRPCOps rpcProxy;

    private void login(String username, String password) {
        connection = client.start(username, password);
        rpcProxy = connection.getProxy();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        node = startNode(ALICE_NAME, 1, singletonList(rpcUser));
        client = new CordaRPCClient(requireNonNull(node.getInternals().getConfiguration().getRpcOptions().getAddress()));
    }

    @After
    public void done() throws IOException {
        connection.close();
    }

    @Test
    public void testLogin() {
        login(rpcUser.getUsername(), rpcUser.getPassword());
    }

    @Test
    public void testCashBalances() throws ExecutionException, InterruptedException {
        login(rpcUser.getUsername(), rpcUser.getPassword());

        FlowHandle<AbstractCashFlow.Result> flowHandle = rpcProxy.startFlowDynamic(CashIssueFlow.class,
                DOLLARS(123), OpaqueBytes.of((byte)0),
                InternalTestUtilsKt.chooseIdentity(node.getInfo()));
        System.out.println("Started issuing cash, waiting on result");
        flowHandle.getReturnValue().get();

        Amount<Currency> balance = getCashBalance(rpcProxy, Currency.getInstance("USD"));
        System.out.print("Balance: " + balance + "\n");

        assertEquals(DOLLARS(123), balance, "matching");
    }
}
