package net.corda.core.flows;

import net.corda.core.DeleteForDJVM;
import net.corda.core.internal.FlowStateMachine;
import net.corda.core.node.ServiceHub;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

/**
 * Create the {@link ServiceHub} proxy using Java, partly so that we can
 * make this package private but mainly because Java allows us to pass
 * Object[] directly as a vararg Object... parameter without copying it.
 */
@DeleteForDJVM
final class ProxyFactory {
    private ProxyFactory() {}

    static ServiceHub createServiceHub(ClassLoader classLoader, Supplier<FlowStateMachine<?>> stateMachine) {
        return (ServiceHub) Proxy.newProxyInstance(
            classLoader,
            new Class<?>[]{ ServiceHub.class },
            (obj, method, args) -> {
                ServiceHub serviceHub = stateMachine.get().getServiceHub();
                try {
                    return method.invoke(serviceHub, args);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    throw cause != null ? cause : e;
                } catch (IllegalAccessException e) {
                    throw new SecurityException(e.getMessage(), e);
                }
            }
        );
    }
}
