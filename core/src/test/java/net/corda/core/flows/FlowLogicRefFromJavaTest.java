package net.corda.core.flows;

import org.junit.*;

import java.util.*;

public class FlowLogicRefFromJavaTest {

    private static class ParamType1 {
        final int value;

        ParamType1(int v) {
            value = v;
        }
    }

    private static class ParamType2 {
        final String value;

        ParamType2(String v) {
            value = v;
        }
    }

    private static class JavaFlowLogic extends FlowLogic<Void> {

        public JavaFlowLogic(ParamType1 A, ParamType2 b) {
        }

        @Override
        public Void call() {
            return null;
        }
    }

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
        FlowLogicRefFactory factory = new FlowLogicRefFactory(whiteList);
        factory.create(JavaFlowLogic.class, new ParamType1(1), new ParamType2("Hello Jack"));
    }

    @Test
    public void testNoArg() {
        Map<String, Set<String>> whiteList = new HashMap<>();
        whiteList.put(JavaNoArgFlowLogic.class.getName(), new HashSet<>());
        FlowLogicRefFactory factory = new FlowLogicRefFactory(whiteList);
        factory.create(JavaNoArgFlowLogic.class);
    }
}
