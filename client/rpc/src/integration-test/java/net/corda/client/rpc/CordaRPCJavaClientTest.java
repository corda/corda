package net.corda.client.rpc;

import net.corda.core.concurrent.CordaFuture;
import net.corda.client.rpc.internal.RPCClient;
import net.corda.core.contracts.Amount;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.services.ServiceInfo;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.flows.AbstractCashFlow;
import net.corda.flows.CashIssueFlow;
import net.corda.flows.CashPaymentFlow;
import net.corda.node.internal.Node;
import net.corda.node.services.transactions.ValidatingNotaryService;
import net.corda.nodeapi.User;
import net.corda.testing.node.NodeBasedTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static kotlin.test.AssertionsKt.assertEquals;
import static net.corda.client.rpc.CordaRPCClientConfiguration.getDefault;
import static net.corda.contracts.GetBalances.getCashBalance;
import static net.corda.node.services.RPCUserServiceKt.startFlowPermission;
import static net.corda.testing.TestConstants.getALICE;

public class CordaRPCJavaClientTest extends NodeBasedTest {
    private List<String> perms = Arrays.asList(startFlowPermission(CashPaymentFlow.class), startFlowPermission(CashIssueFlow.class));
    private Set<String> permSet = new HashSet<>(perms);
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
        Set<ServiceInfo> services = new HashSet<>(Collections.singletonList(new ServiceInfo(ValidatingNotaryService.Companion.getType(), null)));
        CordaFuture<Node> nodeFuture = startNode(getALICE().getName(), 1, services, Arrays.asList(rpcUser), Collections.emptyMap());
        node = nodeFuture.get();
        client = new CordaRPCClient(node.getConfiguration().getRpcAddress(), null, getDefault(), false);
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

        Amount<Currency> balance = getCashBalance(rpcProxy, Currency.getInstance("USD"));
        System.out.print("Balance: " + balance + "\n");

        assertEquals(dollars123, balance, "matching");
    }
}
