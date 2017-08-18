package net.corda.java.rpc;

import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.contracts.Amount;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.NetworkMapCache;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.flows.AbstractCashFlow;
import net.corda.flows.CashIssueFlow;
import net.corda.nodeapi.User;
import net.corda.smoketesting.NodeConfig;
import net.corda.smoketesting.NodeProcess;
import org.bouncycastle.asn1.x500.X500Name;
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
import static net.corda.contracts.GetBalances.getCashBalance;

public class StandaloneCordaRPCJavaClientTest {
    private List<String> perms = Collections.singletonList("ALL");
    private Set<String> permSet = new HashSet<>(perms);
    private User rpcUser = new User("user1", "test", permSet);

    private AtomicInteger port = new AtomicInteger(15000);

    private NodeProcess.Factory factory;
    private NodeProcess notary;
    private CordaRPCOps rpcProxy;
    private CordaRPCConnection connection;
    private NodeInfo notaryNode;

    private NodeConfig notaryConfig = new NodeConfig(
            new X500Name("CN=Notary Service,O=R3,OU=corda,L=Zurich,C=CH"),
            port.getAndIncrement(),
            port.getAndIncrement(),
            port.getAndIncrement(),
            Collections.singletonList("corda.notary.validating"),
            Arrays.asList(rpcUser),
            null
    );

    @Before
    public void setUp() {
        factory = new NodeProcess.Factory();
        copyFinanceCordapp();
        notary = factory.create(notaryConfig);
        connection = notary.connect();
        rpcProxy = connection.getProxy();
        notaryNode = fetchNotaryIdentity();
    }

    @After
    public void done() {
        try {
            connection.close();
        } finally {
            notary.close();
        }
    }

    private void copyFinanceCordapp() {
        Path pluginsDir = (factory.baseDirectory(notaryConfig).resolve("plugins"));
        try {
            Files.createDirectories(pluginsDir);
        } catch (IOException ex) {
            fail("Failed to create directories");
        }
        try (Stream<Path> paths = Files.walk(Paths.get("build", "resources", "smokeTest"))) {
            paths.forEach(file -> {
                if (file.toString().contains("corda-finance")) {
                    try {
                        Files.copy(file, pluginsDir.resolve(file.getFileName()));
                    } catch (IOException ex) {
                        fail("Failed to copy finance jar");
                    }
                }
            });
        } catch (IOException e) {
            fail("Failed to walk files");
        }
    }

    private NodeInfo fetchNotaryIdentity() {
        List<NodeInfo> nodeDataSnapshot = rpcProxy.networkMapSnapshot();
        return nodeDataSnapshot.get(0);
    }

    @Test
    public void testCashBalances() throws NoSuchFieldException, ExecutionException, InterruptedException {
        Amount<Currency> dollars123 = new Amount<>(123, Currency.getInstance("USD"));

        FlowHandle<AbstractCashFlow.Result> flowHandle = rpcProxy.startFlowDynamic(CashIssueFlow.class,
                dollars123, OpaqueBytes.of("1".getBytes()),
                notaryNode.getLegalIdentity());
        System.out.println("Started issuing cash, waiting on result");
        flowHandle.getReturnValue().get();

        Amount<Currency> balance = getCashBalance(rpcProxy, Currency.getInstance("USD"));
        System.out.print("Balance: " + balance + "\n");

        assertEquals(dollars123, balance, "matching");
    }
}
