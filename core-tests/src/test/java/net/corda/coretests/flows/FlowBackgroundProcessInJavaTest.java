package net.corda.coretests.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.flows.FlowExternalFuture;
import net.corda.core.flows.FlowExternalResult;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
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

import java.io.Serializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.BiFunction;

import static net.corda.testing.driver.Driver.driver;
import static org.junit.Assert.assertEquals;

public class FlowBackgroundProcessInJavaTest extends AbstractFlowExternalResultTest {

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
                    FlowWithExternalResultInJava.class,
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
                    FlowWithExternalFutureInJava.class,
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
                    FlowWithExternalResultThatGetsRetriedInJava.class,
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
    public static class FlowWithExternalResultInJava extends FlowWithExternalProcess {

        private static Logger log = LoggerFactory.getLogger(FlowWithExternalResultInJava.class);

        public FlowWithExternalResultInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await(
                    new ExternalResult<>(
                            getServiceHub().cordaService(FutureService.class),
                            (BiFunction<FutureService, String, Object> & Serializable) (futureService, deduplicationId) -> {
                                log.info("Inside of background process -  " + deduplicationId);
                                return "Background process completed - (" + deduplicationId + ")";
                            }
                    )
            );
        }
    }

    @StartableByRPC
    public static class FlowWithExternalFutureInJava extends FlowWithExternalProcess {

        public FlowWithExternalFutureInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await(new ExternalFuture<>(
                    getServiceHub().cordaService(FutureService.class),
                    (BiFunction<FutureService, String, CordaFuture<Object>> & Serializable) (futureService, deduplicationId) ->
                            futureService.createFuture()
            ));
        }
    }

    @StartableByRPC
    public static class FlowWithExternalResultThatGetsRetriedInJava extends FlowWithExternalProcess {

        private static boolean flag = false;

        public FlowWithExternalResultThatGetsRetriedInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await(
                    new ExternalResult<>(
                            getServiceHub().cordaService(FutureService.class),
                            (BiFunction<FutureService, String, Object> & Serializable) (futureService, deduplicationId) -> {
                                if (!flag) {
                                    flag = true;
                                    return futureService.throwHospitalHandledException();
                                } else {
                                    return "finished";
                                }
                            }
                    )
            );
        }
    }

    public static class ExternalFuture<R> implements FlowExternalFuture<R> {

        private FutureService futureService;
        private BiFunction<FutureService, String, CordaFuture<R>> operation;

        public ExternalFuture(FutureService futureService, BiFunction<FutureService, String, CordaFuture<R>> operation) {
            this.futureService = futureService;
            this.operation = operation;
        }

        @NotNull
        @Override
        public CordaFuture<R> execute(@NotNull String deduplicationId) {
            return operation.apply(futureService, deduplicationId);
        }
    }

    public static class ExternalResult<R> implements FlowExternalResult<R> {

        private FutureService futureService;
        private BiFunction<FutureService, String, R> operation;

        public ExternalResult(FutureService futureService, BiFunction<FutureService, String, R> operation) {
            this.futureService = futureService;
            this.operation = operation;
        }

        @NotNull
        @Override
        public R execute(@NotNull String deduplicationId) {
            return operation.apply(futureService, deduplicationId);
        }
    }
}