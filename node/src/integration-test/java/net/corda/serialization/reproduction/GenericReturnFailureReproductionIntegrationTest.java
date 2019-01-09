package net.corda.serialization.reproduction;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.serialization.CordaSerializable;
import net.corda.node.services.Permissions;
import net.corda.testing.driver.Driver;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import net.corda.testing.internal.IntegrationTest;
import net.corda.testing.internal.IntegrationTestSchemas;
import net.corda.testing.node.User;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.corda.testing.core.TestConstants.*;

public class GenericReturnFailureReproductionIntegrationTest extends IntegrationTest {
    @ClassRule
    public static final IntegrationTestSchemas databaseSchemas = new IntegrationTestSchemas(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME);

    @Test()
    public void flowShouldReturnGenericList() {
        User user = new User("yes", "yes", Collections.singleton(Permissions.startFlow(SuperSimpleGenericFlow.class)));
        DriverParameters defaultParameters = new DriverParameters();
        Driver.<Void>driver(defaultParameters, (driver) -> {
            NodeHandle startedNode = getOrThrow(driver.startNode(new NodeParameters().withRpcUsers(Collections.singletonList(user)).withStartInSameProcess(true)));
            (new CordaRPCClient(startedNode.getRpcAddress())).<Void>use("yes", "yes", (cordaRPCConnection -> {
                getOrThrow(cordaRPCConnection.getProxy().startFlowDynamic(SuperSimpleGenericFlow.class).getReturnValue());
                return null;
            }));
            return null;
        });

    }

    private static <Y> Y getOrThrow(CordaFuture<Y> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
