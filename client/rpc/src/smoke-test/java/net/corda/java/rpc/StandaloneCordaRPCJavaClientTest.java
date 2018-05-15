/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.java.rpc;

import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.finance.flows.AbstractCashFlow;
import net.corda.finance.flows.CashIssueFlow;
import net.corda.nodeapi.internal.config.User;
import net.corda.smoketesting.NodeConfig;
import net.corda.smoketesting.NodeProcess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static kotlin.test.AssertionsKt.assertEquals;
import static kotlin.test.AssertionsKt.fail;
import static net.corda.finance.contracts.GetBalances.getCashBalance;

public class StandaloneCordaRPCJavaClientTest {
    private List<String> perms = Collections.singletonList("ALL");
    private Set<String> permSet = new HashSet<>(perms);
    private User rpcUser = new User("user1", "test", permSet);

    private AtomicInteger port = new AtomicInteger(15000);

    private NodeProcess.Factory factory;
    private NodeProcess notary;
    private CordaRPCOps rpcProxy;
    private CordaRPCConnection connection;
    private Party notaryNodeIdentity;

    private NodeConfig notaryConfig = new NodeConfig(
            new CordaX500Name("Notary Service", "Zurich", "CH"),
            port.getAndIncrement(),
            port.getAndIncrement(),
            port.getAndIncrement(),
            true,
            Collections.singletonList(rpcUser),
            true,
            true,
            Collections.emptyList()
    );

    @Before
    public void setUp() {
        factory = new NodeProcess.Factory();
        copyFinanceCordapp();
        notary = factory.create(notaryConfig);
        connection = notary.connect();
        rpcProxy = connection.getProxy();
        notaryNodeIdentity = rpcProxy.nodeInfo().getLegalIdentities().get(0);
    }

    @After
    public void done() {
        try {
            connection.close();
        } finally {
            if (notary != null) {
                notary.close();
            }
        }
    }

    private void copyFinanceCordapp() {
        Path cordappsDir = (factory.baseDirectory(notaryConfig).resolve(NodeProcess.CORDAPPS_DIR_NAME));
        try {
            Files.createDirectories(cordappsDir);
        } catch (IOException ex) {
            fail("Failed to create directories");
        }
        try (Stream<Path> paths = Files.walk(Paths.get("build", "resources", "smokeTest"))) {
            paths.forEach(file -> {
                if (file.toString().contains("corda-finance")) {
                    try {
                        Files.copy(file, cordappsDir.resolve(file.getFileName()));
                    } catch (IOException ex) {
                        fail("Failed to copy finance jar");
                    }
                }
            });
        } catch (IOException e) {
            fail("Failed to walk files");
        }
    }

    @Test
    public void testCashBalances() throws NoSuchFieldException, ExecutionException, InterruptedException {
        Amount<Currency> dollars123 = new Amount<>(123, Currency.getInstance("USD"));

        FlowHandle<AbstractCashFlow.Result> flowHandle = rpcProxy.startFlowDynamic(CashIssueFlow.class,
                dollars123, OpaqueBytes.of("1".getBytes()),
                notaryNodeIdentity);
        System.out.println("Started issuing cash, waiting on result");
        flowHandle.getReturnValue().get();

        Amount<Currency> balance = getCashBalance(rpcProxy, Currency.getInstance("USD"));
        System.out.print("Balance: " + balance + "\n");

        assertEquals(dollars123, balance, "matching");
    }
}
