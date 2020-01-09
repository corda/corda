package net.corda.coretests.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.ServiceHub;
import net.corda.core.utilities.KotlinUtilsKt;
import net.corda.testing.core.TestConstants;
import net.corda.testing.core.TestUtils;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static net.corda.testing.driver.Driver.driver;
import static org.junit.Assert.assertEquals;

public class FlowBackgroundProcessInJavaTest {

    @Test
    public void awaitCalledFromJava() {
        driver(new DriverParameters().withStartNodesInProcess(true), driver -> {
            NodeHandle alice = KotlinUtilsKt.getOrThrow(
                    driver.startNode(new NodeParameters().withProvidedName(TestConstants.ALICE_NAME)),
                    Duration.of(20, ChronoUnit.SECONDS)
            );
            NodeHandle bob = KotlinUtilsKt.getOrThrow(
                    driver.startNode(new NodeParameters().withProvidedName(TestConstants.BOB_NAME)),
                    Duration.of(20, ChronoUnit.SECONDS)
            );
            return KotlinUtilsKt.getOrThrow(alice.getRpc().startFlowDynamic(
                    FlowWithBackgroundProcessInJava.class,
                    TestUtils.singleIdentity(bob.getNodeInfo())
            ).getReturnValue(), Duration.of(20, ChronoUnit.SECONDS));
        });
    }

    @Test
    public void awaitFutureCalledFromJava() {
        driver(new DriverParameters().withStartNodesInProcess(true), driver -> {
            NodeHandle alice = KotlinUtilsKt.getOrThrow(
                    driver.startNode(new NodeParameters().withProvidedName(TestConstants.ALICE_NAME)),
                    Duration.of(20, ChronoUnit.SECONDS)
            );
            NodeHandle bob = KotlinUtilsKt.getOrThrow(
                    driver.startNode(new NodeParameters().withProvidedName(TestConstants.BOB_NAME)),
                    Duration.of(20, ChronoUnit.SECONDS)
            );
            return KotlinUtilsKt.getOrThrow(alice.getRpc().startFlowDynamic(
                    FlowWithBackgroundProcessFutureInJava.class,
                    TestUtils.singleIdentity(bob.getNodeInfo())
            ).getReturnValue(), Duration.of(20, ChronoUnit.SECONDS));
        });
    }

    @Test
    public void awaitCalledFromJavaWithMethodReference() {
        driver(new DriverParameters().withStartNodesInProcess(true), driver -> {
            NodeHandle alice = KotlinUtilsKt.getOrThrow(
                    driver.startNode(new NodeParameters().withProvidedName(TestConstants.ALICE_NAME)),
                    Duration.of(20, ChronoUnit.SECONDS)
            );
            NodeHandle bob = KotlinUtilsKt.getOrThrow(
                    driver.startNode(new NodeParameters().withProvidedName(TestConstants.BOB_NAME)),
                    Duration.of(20, ChronoUnit.SECONDS)
            );
            return KotlinUtilsKt.getOrThrow(alice.getRpc().startFlowDynamic(
                    FlowWithBackgroundProcessDefinedAsMethodReferenceInJava.class,
                    TestUtils.singleIdentity(bob.getNodeInfo())
            ).getReturnValue(), Duration.of(20, ChronoUnit.SECONDS));
        });
    }

    @Test
    public void awaitCalledFromJavaCanBeRetried() {
        driver(new DriverParameters().withStartNodesInProcess(true), driver -> {
            NodeHandle alice = KotlinUtilsKt.getOrThrow(
                    driver.startNode(new NodeParameters().withProvidedName(TestConstants.ALICE_NAME)),
                    Duration.of(20, ChronoUnit.SECONDS)
            );
            NodeHandle bob = KotlinUtilsKt.getOrThrow(
                    driver.startNode(new NodeParameters().withProvidedName(TestConstants.BOB_NAME)),
                    Duration.of(20, ChronoUnit.SECONDS)
            );
            KotlinUtilsKt.getOrThrow(alice.getRpc().startFlowDynamic(
                    FlowWithBackgroundProcessThatGetsRetriedInJava.class,
                    TestUtils.singleIdentity(bob.getNodeInfo())
            ).getReturnValue(), Duration.of(20, ChronoUnit.SECONDS));

            HospitalCounts counts = KotlinUtilsKt.getOrThrow(alice.getRpc().startFlowDynamic(
                    GetHospitalCountersFlow.class
            ).getReturnValue(), Duration.of(20, ChronoUnit.SECONDS));
            assertEquals(1, counts.getDischarge());
            assertEquals(0, counts.getObservation());

            return null;
        });
    }

    @StartableByRPC
    public static class FlowWithBackgroundProcessInJava extends FlowWithBackgroundProcess {

        private static Logger log = LoggerFactory.getLogger(FlowWithBackgroundProcessInJava.class);

        public FlowWithBackgroundProcessInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await((serviceHub, deduplicationId) -> {
                log.info("Inside of background process - {}", deduplicationId);
                return "Background process completed - (" + deduplicationId + ")";
            });
        }
    }

    @StartableByRPC
    public static class FlowWithBackgroundProcessFutureInJava extends FlowWithBackgroundProcess {

        public FlowWithBackgroundProcessFutureInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return awaitFuture((serviceHub, deduplicationId) -> serviceHub.cordaService(FutureService.class).createFuture());
        }
    }

    @StartableByRPC
    public static class FlowWithBackgroundProcessDefinedAsMethodReferenceInJava extends FlowWithBackgroundProcess {

        public FlowWithBackgroundProcessDefinedAsMethodReferenceInJava(Party party) {
            super(party);
        }

        private String invoke(ServiceHub serviceHub, String deduplicationId) {
            return "Background process completed - ($deduplicationId)";
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await(this::invoke);
        }
    }

    @StartableByRPC
    public static class FlowWithBackgroundProcessThatGetsRetriedInJava extends FlowWithBackgroundProcess {

        private static boolean flag = false;

        public FlowWithBackgroundProcessThatGetsRetriedInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await((serviceHub, deduplicationId) -> {
                if (!flag) {
                    flag = true;
                    return serviceHub.cordaService(FutureService.class).throwHospitalHandledException();
                } else {
                    return "finished";
                }
            });
        }
    }
}