package net.corda.nodeapi.internal.serialization.amqp;

import net.corda.nodeapi.internal.serialization.AllWhitelist;
import net.corda.nodeapi.internal.serialization.amqp.testutils.TestSerializationContext;
import net.corda.nodeapi.internal.serialization.amqp.testutils.TestSerializationContextKt;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import java.io.NotSerializableException;

@Ignore("Current behaviour allows for the serialization of objects with private members, this will be disallowed at some point in the future")
public class ErrorMessageTests {
    private String errMsg(String property, String testname) {
        return "Property '"
                + property
                + "' or its getter is non public, this renders class 'class "
                + testname
                + "$C' unserializable -> class "
                + testname
                + "$C";
    }

    static class C {
        public Integer a;

        public C(Integer a) {
            this.a = a;
        }

        private Integer getA() { return this.a; }
    }

    @Test
    public void testJavaConstructorAnnotations() {
        EvolutionSerializerGetterBase evolutionSerialiserGetter = new EvolutionSerializerGetter();
        FingerPrinter fingerPrinter = new SerializerFingerPrinter();
        SerializerFactory factory1 = new SerializerFactory(
                AllWhitelist.INSTANCE,
                ClassLoader.getSystemClassLoader(),
                evolutionSerialiserGetter,
                fingerPrinter);

        SerializationOutput ser = new SerializationOutput(factory1);

        Assertions.assertThatThrownBy(() -> ser.serialize(new C(1), TestSerializationContext.testSerializationContext))
                .isInstanceOf(NotSerializableException.class)
                .hasMessage(errMsg("a", getClass().getName()));
    }

}
