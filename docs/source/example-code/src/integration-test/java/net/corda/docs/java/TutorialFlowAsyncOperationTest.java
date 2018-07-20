package net.corda.docs.java;

import kotlin.Unit;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.utilities.KotlinUtilsKt;
import net.corda.docs.java.tutorial.flowstatemachines.ExampleSummingFlow;
import net.corda.node.services.Permissions;
import net.corda.testing.driver.*;
import net.corda.testing.internal.IntegrationTest;
import net.corda.testing.internal.IntegrationTestKt;
import net.corda.testing.internal.IntegrationTestSchemas;
import net.corda.testing.node.User;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Future;

import static net.corda.testing.core.TestConstants.ALICE_NAME;
import static net.corda.testing.core.TestConstants.DUMMY_NOTARY_NAME;
import static org.junit.Assert.assertEquals;

public final class TutorialFlowAsyncOperationTest extends IntegrationTest {

    @ClassRule
    public static IntegrationTestSchemas databaseSchemas = new IntegrationTestSchemas(IntegrationTestKt.toDatabaseSchemaName(ALICE_NAME), IntegrationTestKt.toDatabaseSchemaName(DUMMY_NOTARY_NAME));

    // DOCSTART summingWorks
    @Test
    public final void summingWorks() {
        Driver.driver(new DriverParameters(), (DriverDSL dsl) -> {
            User aliceUser = new User("aliceUser", "testPassword1",
                    new HashSet<>(Collections.singletonList(Permissions.all()))
            );
            Future<NodeHandle> aliceFuture = dsl.startNode(new NodeParameters()
                    .withProvidedName(ALICE_NAME)
                    .withRpcUsers(Collections.singletonList(aliceUser))
            );
            NodeHandle alice = KotlinUtilsKt.getOrThrow(aliceFuture, null);
            CordaRPCClient aliceClient = new CordaRPCClient(alice.getRpcAddress());
            CordaRPCOps aliceProxy = aliceClient.start("aliceUser", "testPassword1").getProxy();
            Future<Integer> answerFuture = aliceProxy.startFlowDynamic(ExampleSummingFlow.class).getReturnValue();
            int answer = KotlinUtilsKt.getOrThrow(answerFuture, null);
            assertEquals(3, answer);
            return Unit.INSTANCE;
        });
    }
    // DOCEND summingWorks
}
