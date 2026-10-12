package net.corda.java.rpc;

import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.finance.flows.AbstractCashFlow;
import net.corda.finance.flows.CashIssueFlow;
import net.corda.nodeapi.internal.config.User;
import net.corda.smoketesting.NodeParams;
import net.corda.smoketesting.NodeProcess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Currency;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singletonList;
import static kotlin.test.AssertionsKt.assertEquals;
import static net.corda.finance.workflows.GetBalances.getCashBalance;
import static net.corda.kotlin.rpc.StandaloneCordaRPClientTest.gatherCordapps;

public class StandaloneCordaRPCJavaClientTest {
    private final User superUser = new User("superUser", "test", new HashSet<>(singletonList("ALL")));

    private final AtomicInteger port = new AtomicInteger(15000);
    private final NodeProcess.Factory factory = new NodeProcess.Factory();

    private CordaRPCOps rpcProxy;
    private Party notaryNodeIdentity;

    @Before
    public void setUp() {
        NodeProcess notary = factory.createNotaries(new NodeParams(
                new CordaX500Name("Notary Service", "Zurich", "CH"),
                port.getAndIncrement(),
                port.getAndIncrement(),
                port.getAndIncrement(),
                singletonList(superUser),
                gatherCordapps()
        )).get(0);
        rpcProxy = notary.connect(superUser).getProxy();
        notaryNodeIdentity = rpcProxy.nodeInfo().getLegalIdentities().get(0);
    }

    @After
    public void done() {
        factory.close();
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
