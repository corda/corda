package net.corda.coretests.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowExternalAsyncOperation;
import net.corda.core.flows.FlowExternalOperation;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.utilities.KotlinUtilsKt;
import net.corda.testing.core.TestConstants;
import net.corda.testing.core.TestUtils;
import net.corda.testing.driver.DriverDSL;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static net.corda.testing.driver.Driver.driver;

public class FlowExternalOperationInJavaTest extends AbstractFlowExternalOperationTest {

    @Test
    public void awaitFlowExternalOperationInJava() {
        driver(new DriverParameters().withStartNodesInProcess(true), driver -> {
            List<NodeHandle> aliceAndBob = aliceAndBob(driver);
            NodeHandle alice = aliceAndBob.get(0);
            NodeHandle bob = aliceAndBob.get(1);
            return KotlinUtilsKt.getOrThrow(alice.getRpc().startFlowDynamic(
                    FlowWithExternalOperationInJava.class,
                    TestUtils.singleIdentity(bob.getNodeInfo())
            ).getReturnValue(), Duration.of(1, ChronoUnit.MINUTES));
        });
    }

    @Test
    public void awaitFlowExternalAsyncOperationInJava() {
        driver(new DriverParameters().withStartNodesInProcess(true), driver -> {
            List<NodeHandle> aliceAndBob = aliceAndBob(driver);
            NodeHandle alice = aliceAndBob.get(0);
            NodeHandle bob = aliceAndBob.get(1);
            return KotlinUtilsKt.getOrThrow(alice.getRpc().startFlowDynamic(
                    FlowWithExternalAsyncOperationInJava.class,
                    TestUtils.singleIdentity(bob.getNodeInfo())
            ).getReturnValue(), Duration.of(1, ChronoUnit.MINUTES));
        });
    }

    @Test
    public void awaitFlowExternalOperationInJavaCanBeRetried() {
        driver(new DriverParameters().withStartNodesInProcess(true), driver -> {
            List<NodeHandle> aliceAndBob = aliceAndBob(driver);
            NodeHandle alice = aliceAndBob.get(0);
            NodeHandle bob = aliceAndBob.get(1);
            KotlinUtilsKt.getOrThrow(alice.getRpc().startFlowDynamic(
                    FlowWithExternalOperationThatGetsRetriedInJava.class,
                    TestUtils.singleIdentity(bob.getNodeInfo())
            ).getReturnValue(), Duration.of(1, ChronoUnit.MINUTES));

            assertHospitalCounters(1, 0);

            return null;
        });
    }

    @StartableByRPC
    public static class FlowWithExternalOperationInJava extends FlowWithExternalProcess {

        private static Logger log = LoggerFactory.getLogger(FlowWithExternalOperationInJava.class);

        public FlowWithExternalOperationInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await(
                    new ExternalOperation<>(
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
    public static class FlowWithExternalAsyncOperationInJava extends FlowWithExternalProcess {

        public FlowWithExternalAsyncOperationInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await(new ExternalAsyncOperation<>(
                    getServiceHub().cordaService(FutureService.class),
                    (BiFunction<FutureService, String, CompletableFuture<Object>> & Serializable) (futureService, deduplicationId) ->
                            futureService.createFuture()
            ));
        }
    }

    @StartableByRPC
    public static class FlowWithExternalOperationThatGetsRetriedInJava extends FlowWithExternalProcess {

        private static boolean flag = false;

        public FlowWithExternalOperationThatGetsRetriedInJava(Party party) {
            super(party);
        }

        @NotNull
        @Override
        @Suspendable
        public Object testCode() {
            return await(
                    new ExternalOperation<>(
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

    public static class ExternalAsyncOperation<R> implements FlowExternalAsyncOperation<R> {

        private FutureService futureService;
        private BiFunction<FutureService, String, CompletableFuture<R>> operation;

        public ExternalAsyncOperation(FutureService futureService, BiFunction<FutureService, String, CompletableFuture<R>> operation) {
            this.futureService = futureService;
            this.operation = operation;
        }

        @NotNull
        @Override
        public CompletableFuture<R> execute(@NotNull String deduplicationId) {
            return operation.apply(futureService, deduplicationId);
        }
    }

    public static class ExternalOperation<R> implements FlowExternalOperation<R> {

        private FutureService futureService;
        private BiFunction<FutureService, String, R> operation;

        public ExternalOperation(FutureService futureService, BiFunction<FutureService, String, R> operation) {
            this.futureService = futureService;
            this.operation = operation;
        }

        @NotNull
        @Override
        public R execute(@NotNull String deduplicationId) {
            return operation.apply(futureService, deduplicationId);
        }
    }

    private List<NodeHandle> aliceAndBob(DriverDSL driver) {
        return Arrays.asList(TestConstants.ALICE_NAME, TestConstants.BOB_NAME)
                .stream()
                .map(nm -> driver.startNode(new NodeParameters().withProvidedName(nm)))
                .collect(Collectors.toList())
                .stream()
                .map(future -> KotlinUtilsKt.getOrThrow(future,
                        Duration.of(1, ChronoUnit.MINUTES)))
                .collect(Collectors.toList());
    }
}