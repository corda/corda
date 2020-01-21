package net.corda.node.services;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.node.AppServiceHub;
import net.corda.core.node.services.CordaService;
import net.corda.core.node.services.ServiceLifecycleEvent;
import net.corda.core.serialization.SingletonSerializeAsToken;

import java.util.ArrayList;
import java.util.List;

import static net.corda.core.node.AppServiceHub.SERVICE_PRIORITY_NORMAL;

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

        private final AppServiceHub serviceHub;

        public JavaTextLengthComputingService(AppServiceHub serviceHub) {
            this.serviceHub = serviceHub;
            serviceHub.register(SERVICE_PRIORITY_NORMAL, this::addEvent);
        }

        private void addEvent(ServiceLifecycleEvent event) {

            switch (event) {
                case STATE_MACHINE_STARTED:
                    eventsCaptured.add(event);
                    break;
                default:
                    // Process other typed of events
                    break;
            }
        }

        public int computeLength(String text) {
            assert !text.isEmpty();
            return text.length();
        }
    }
}