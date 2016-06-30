package com.r3corda.core.protocols;


import com.google.common.collect.Sets;
import org.junit.Test;

public class ProtocolLogicRefFromJavaTest {

    public static class JavaProtocolLogic extends ProtocolLogic<Void> {

        public JavaProtocolLogic(int A, String b) {
        }

        @Override
        public Void call() {
            return null;
        }
    }

    public static class JavaNoArgProtocolLogic extends ProtocolLogic<Void> {

        public JavaNoArgProtocolLogic() {
        }

        @Override
        public Void call() {
            return null;
        }
    }

    @Test
    public void test() {
        ProtocolLogicRefFactory factory = new ProtocolLogicRefFactory(Sets.newHashSet(JavaProtocolLogic.class.getName()), Sets.newHashSet(Integer.class.getName(), String.class.getName()));
        factory.create(JavaProtocolLogic.class, 1, "Hello Jack");
    }

    @Test
    public void testNoArg() {
        ProtocolLogicRefFactory factory = new ProtocolLogicRefFactory(Sets.newHashSet(JavaNoArgProtocolLogic.class.getName()), Sets.newHashSet(Integer.class.getName(), String.class.getName()));
        factory.create(JavaNoArgProtocolLogic.class);
    }
}
