package net.corda.client.rpc;

import com.google.common.util.concurrent.*;
import net.corda.client.rpc.internal.*;
import net.corda.contracts.asset.*;
import net.corda.core.contracts.*;
import net.corda.core.messaging.*;
import net.corda.core.node.services.*;
import net.corda.core.node.services.vault.*;
import net.corda.core.utilities.*;
import net.corda.flows.*;
import net.corda.node.internal.*;
import net.corda.node.services.transactions.*;
import net.corda.nodeapi.*;
import net.corda.schemas.*;
import net.corda.testing.node.*;
import org.junit.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import static kotlin.test.AssertionsKt.*;
import static net.corda.client.rpc.CordaRPCClientConfiguration.*;
import static net.corda.node.services.RPCUserServiceKt.*;
import static net.corda.testing.TestConstants.*;

public class CordaRPCJavaClientTest extends NodeBasedTest {

    private List<String> perms = Arrays.asList(startFlowPermission(CashPaymentFlow.class), startFlowPermission(CashIssueFlow.class));
    private Set<String> permSet = new HashSet(perms);
    private User rpcUser = new User("user1", "test", permSet);

    private Node node;
    private CordaRPCClient client;
    private RPCClient.RPCConnection<CordaRPCOps> connection = null;
    private CordaRPCOps rpcProxy;

    private void login(String username, String password) {
        connection = client.start(username, password);
        rpcProxy = connection.getProxy();
    }

    @Before
    public void setUp() throws ExecutionException, InterruptedException {
        Set services = new HashSet(Collections.singletonList(new ServiceInfo(ValidatingNotaryService.Companion.getType(), null)));
        ListenableFuture<Node> nodeFuture = startNode(getALICE().getName(), 1, services, Arrays.asList(rpcUser), Collections.emptyMap());
        node = nodeFuture.get();
        client = new CordaRPCClient(node.getConfiguration().getRpcAddress(), null, getDefault());
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
    public void testCashBalances() throws NoSuchFieldException, ExecutionException, InterruptedException {
        login(rpcUser.getUsername(), rpcUser.getPassword());

        Amount<Currency> dollars123 = new Amount<>(123, Currency.getInstance("USD"));

        FlowHandle<AbstractCashFlow.Result> flowHandle = rpcProxy.startFlowDynamic(CashIssueFlow.class,
                dollars123, OpaqueBytes.of("1".getBytes()),
                node.info.getLegalIdentity(), node.info.getLegalIdentity());
        System.out.println("Started issuing cash, waiting on result");
        flowHandle.getReturnValue().get();

        Amount<Currency> balance = getBalance(Currency.getInstance("USD"));
        System.out.print("Balance: " + balance + "\n");

        assertEquals(dollars123, balance, "matching");
    }

    private Amount<Currency> getBalance(Currency currency) throws NoSuchFieldException {

        Field pennies = CashSchemaV1.PersistentCashState.class.getDeclaredField("pennies");
        QueryCriteria sumCriteria = new QueryCriteria.VaultCustomQueryCriteria(Builder.sum(pennies));

        Vault.Page<Cash.State> results = rpcProxy.vaultQueryByCriteria(sumCriteria, Cash.State.class);
        if (results.getOtherResults().isEmpty()) {
            return new Amount(0L, currency);
        } else {
            Assert.assertNotNull(results.getOtherResults());
            Long quantity = (Long) results.getOtherResults().get(0);
            return new Amount(quantity, currency);
        }
    }
}
