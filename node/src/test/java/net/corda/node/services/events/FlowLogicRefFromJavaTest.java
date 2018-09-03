package net.corda.node.services.events;

import net.corda.core.flows.FlowLogic;
import net.corda.node.services.statemachine.FlowLogicRefFactoryImpl;
import org.junit.Test;

public class FlowLogicRefFromJavaTest {

    @SuppressWarnings("unused")
    private static class ParamType1 {
        final int value;

        ParamType1(int v) {
            value = v;
        }
    }

    @SuppressWarnings("unused")
    private static class ParamType2 {
        final String value;

        ParamType2(String v) {
            value = v;
        }
    }

    @SuppressWarnings("unused")
    private static class JavaFlowLogic extends FlowLogic<Void> {

        public JavaFlowLogic(ParamType1 A, ParamType2 b) {
        }

        @Override
        public Void call() {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static class JavaNoArgFlowLogic extends FlowLogic<Void> {

        public JavaNoArgFlowLogic() {
        }

        @Override
        public Void call() {
            return null;
        }
    }

    private final FlowLogicRefFactoryImpl flowLogicRefFactory = new FlowLogicRefFactoryImpl(FlowLogicRefFactoryImpl.class.getClassLoader());

    @Test
    public void test() {
        flowLogicRefFactory.createForRPC(JavaFlowLogic.class, new ParamType1(1), new ParamType2("Hello Jack"));
    }

    @Test
    public void testNoArg() {
        flowLogicRefFactory.createForRPC(JavaNoArgFlowLogic.class);
    }
}
