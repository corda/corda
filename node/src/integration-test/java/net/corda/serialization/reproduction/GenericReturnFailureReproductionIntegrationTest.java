package net.corda.serialization.reproduction;

import net.corda.client.rpc.CordaRPCClient;
import net.corda.core.concurrent.CordaFuture;
import net.corda.node.services.Permissions;
import net.corda.testing.driver.Driver;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import net.corda.testing.node.User;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class GenericReturnFailureReproductionIntegrationTest {

    @Test()
    public void flowShouldReturnGenericList() {
        User user = new User("yes", "yes", Collections.singleton(Permissions.startFlow(SuperSimpleGenericFlow.class)));
        DriverParameters defaultParameters = new DriverParameters();
        Driver.<Void>driver(defaultParameters.withStartNodesInProcess(true), (driver) -> {
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
