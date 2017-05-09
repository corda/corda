package net.corda.node.services.events;

import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowLogicRefFactory;
import net.corda.node.services.statemachine.FlowLogicRefFactoryImpl;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    @Test
    public void test() {
        Map<String, Set<String>> whiteList = new HashMap<>();
        Set<String> argsList = new HashSet<>();
        argsList.add(ParamType1.class.getName());
        argsList.add(ParamType2.class.getName());
        whiteList.put(JavaFlowLogic.class.getName(), argsList);
        FlowLogicRefFactory factory = new FlowLogicRefFactoryImpl(whiteList);
        factory.create(JavaFlowLogic.class, new ParamType1(1), new ParamType2("Hello Jack"));
    }

    @Test
    public void testNoArg() {
        Map<String, Set<String>> whiteList = new HashMap<>();
        whiteList.put(JavaNoArgFlowLogic.class.getName(), new HashSet<>());
        FlowLogicRefFactory factory = new FlowLogicRefFactoryImpl(whiteList);
        factory.create(JavaNoArgFlowLogic.class);
    }
}
