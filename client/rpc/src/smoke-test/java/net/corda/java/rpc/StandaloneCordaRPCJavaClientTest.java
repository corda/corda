package net.corda.java.rpc;

import net.corda.client.rpc.*;
import net.corda.contracts.asset.*;
import net.corda.core.contracts.*;
import net.corda.core.messaging.*;
import net.corda.core.node.*;
import net.corda.core.node.services.*;
import net.corda.core.node.services.vault.*;
import net.corda.core.utilities.*;
import net.corda.flows.*;
import net.corda.nodeapi.*;
import net.corda.schemas.*;
import net.corda.smoketesting.*;
import org.bouncycastle.asn1.x500.*;
import org.junit.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static kotlin.test.AssertionsKt.assertEquals;

public class StandaloneCordaRPCJavaClientTest {
    private List<String> perms = Collections.singletonList("ALL");
    private Set<String> permSet = new HashSet<>(perms);
    private User rpcUser = new User("user1", "test", permSet);

    private AtomicInteger port = new AtomicInteger(15000);

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
        notary = new NodeProcess.Factory().create(notaryConfig);
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

    private NodeInfo fetchNotaryIdentity() {
        DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> nodeDataFeed = rpcProxy.networkMapFeed();
        return nodeDataFeed.getSnapshot().get(0);
    }

    @Test
    public void testCashBalances() throws NoSuchFieldException, ExecutionException, InterruptedException {
        Amount<Currency> dollars123 = new Amount<>(123, Currency.getInstance("USD"));

        FlowHandle<AbstractCashFlow.Result> flowHandle = rpcProxy.startFlowDynamic(CashIssueFlow.class,
                dollars123, OpaqueBytes.of("1".getBytes()),
                notaryNode.getLegalIdentity(), notaryNode.getLegalIdentity());
        System.out.println("Started issuing cash, waiting on result");
        flowHandle.getReturnValue().get();

        Amount<Currency> balance = getBalance(Currency.getInstance("USD"));
        System.out.print("Balance: " + balance + "\n");

        assertEquals(dollars123, balance, "matching");
    }

    private Amount<Currency> getBalance(Currency currency) throws NoSuchFieldException {
        Field pennies = CashSchemaV1.PersistentCashState.class.getDeclaredField("pennies");
        @SuppressWarnings("unchecked")
        QueryCriteria sumCriteria = new QueryCriteria.VaultCustomQueryCriteria(Builder.sum(pennies));

        Vault.Page<Cash.State> results = rpcProxy.vaultQueryByCriteria(sumCriteria, Cash.State.class);
        if (results.getOtherResults().isEmpty()) {
            return new Amount<>(0L, currency);
        } else {
            Assert.assertNotNull(results.getOtherResults());
            Long quantity = (Long) results.getOtherResults().get(0);
            return new Amount<>(quantity, currency);
        }
    }
}
