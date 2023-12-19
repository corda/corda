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

import static java.util.Collections.singletonList;
import static kotlin.test.AssertionsKt.assertEquals;
import static kotlin.test.AssertionsKt.fail;
import static net.corda.finance.workflows.GetBalances.getCashBalance;

public class StandaloneCordaRPCJavaClientTest {

    public static void copyCordapps(NodeProcess.Factory factory, NodeConfig notaryConfig) {
        Path cordappsDir = (factory.baseDirectory(notaryConfig).resolve(NodeProcess.CORDAPPS_DIR_NAME));
        try {
            Files.createDirectories(cordappsDir);
        } catch (IOException ex) {
            fail("Failed to create directories");
        }
        try (Stream<Path> paths = Files.walk(Paths.get("build", "resources", "smokeTest"))) {
            paths.filter(path -> path.toFile().getName().startsWith("cordapp")).forEach(file -> {
                try {
                    Files.copy(file, cordappsDir.resolve(file.getFileName()));
                } catch (IOException ex) {
                    fail("Failed to copy cordapp jar");
                }
            });
        } catch (IOException e) {
            fail("Failed to walk files");
        }
    }

    private List<String> perms = singletonList("ALL");
    private Set<String> permSet = new HashSet<>(perms);
    private User superUser = new User("superUser", "test", permSet);

    private AtomicInteger port = new AtomicInteger(15000);

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
            singletonList(superUser),
            true
    );

    @Before
    public void setUp() {
        NodeProcess.Factory factory = new NodeProcess.Factory();
        copyCordapps(factory, notaryConfig);
        notary = factory.create(notaryConfig);
        connection = notary.connect(superUser);
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

    @Test
    public void testCashBalances() throws ExecutionException, InterruptedException {
        Amount<Currency> dollars123 = new Amount<>(123, Currency.getInstance("USD"));

        FlowHandle<AbstractCashFlow.Result> flowHandle = rpcProxy.startFlowDynamic(CashIssueFlow.class,
                dollars123, OpaqueBytes.of("1".getBytes()),
                notaryNodeIdentity);
        System.out.println("Started issuing cash, waiting on result");
        flowHandle.getReturnValue().get();

        Amount<Currency> balance = getCashBalance(rpcProxy, Currency.getInstance("USD"));
        System.out.println("Balance: " + balance);

        assertEquals(dollars123, balance, "matching");
    }
}
