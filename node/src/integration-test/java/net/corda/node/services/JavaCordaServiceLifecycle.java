package net.corda.node.services;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.node.AppServiceHub;
import net.corda.core.node.services.CordaService;
import net.corda.core.node.services.ServiceLifecycleEvent;
import net.corda.core.node.services.ServiceLifecycleObserver;
import net.corda.core.serialization.SingletonSerializeAsToken;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JavaCordaServiceLifecycle {

    static final List<ServiceLifecycleEvent> eventsCaptured = new ArrayList<>();

    @StartableByRPC
    public static class JavaComputeTextLengthThroughCordaService extends  FlowLogic<Integer> {

        private final String text;

        public JavaComputeTextLengthThroughCordaService(String text) {
            this.text = text;
        }

        @Override
        @Suspendable
        public Integer call() {
            JavaTextLengthComputingService service  = getServiceHub().cordaService(JavaTextLengthComputingService.class);
            return service.computeLength(text);
        }
    }

    @CordaService
    public static class JavaTextLengthComputingService extends SingletonSerializeAsToken {

        public JavaTextLengthComputingService(AppServiceHub services) {
            services.register(new ServiceLifecycleObserver() {
                @Override
                public void onServiceLifecycleEvent(@NotNull ServiceLifecycleEvent event) {
                    JavaCordaServiceLifecycle.eventsCaptured.add(event);
                }
            }, false);
        }

        public int computeLength(String text) {
            assert !text.isEmpty();
            return text.length();
        }
    }
}