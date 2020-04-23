package net.corda.docs.java.tutorial.test;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.utilities.KotlinUtilsKt;
import net.corda.docs.java.tutorial.flowstatemachines.ExampleSummingFlow;
import net.corda.node.services.Permissions;
import net.corda.testing.driver.*;
import net.corda.testing.node.User;
import org.junit.Test;

import java.util.concurrent.Future;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static net.corda.testing.core.TestConstants.ALICE_NAME;
import static net.corda.testing.driver.Driver.driver;
import static net.corda.testing.node.internal.InternalTestUtilsKt.cordappWithPackages;
import static org.junit.Assert.assertEquals;

public class TutorialFlowAsyncOperationTest {
    // DOCSTART summingWorks
    @Test
    public void summingWorks() {
        driver(new DriverParameters(singletonList(cordappWithPackages("net.corda.docs.java.tutorial.flowstatemachines"))), (DriverDSL dsl) -> {
            User aliceUser = new User("aliceUser", "testPassword1", singleton(Permissions.all()));
            Future<NodeHandle> aliceFuture = dsl.startNode(new NodeParameters()
                    .withProvidedName(ALICE_NAME)
                    .withRpcUsers(singletonList(aliceUser))
            );
            NodeHandle alice = KotlinUtilsKt.getOrThrow(aliceFuture, null);
            CordaRPCClient aliceClient = new CordaRPCClient(alice.getRpcAddress());
            CordaRPCOps aliceProxy = aliceClient.start("aliceUser", "testPassword1").getProxy();
            Future<Integer> answerFuture = aliceProxy.startFlowDynamic(ExampleSummingFlow.class).getReturnValue();
            int answer = KotlinUtilsKt.getOrThrow(answerFuture, null);
            assertEquals(3, answer);
            return null;
        });
    }
    // DOCEND summingWorks
}
