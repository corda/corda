package net.corda.nodeapi.internal.serialization.amqp;

import net.corda.nodeapi.internal.serialization.AllWhitelist;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.NotSerializableException;

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
        SerializerFactory factory1 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader());
        SerializationOutput ser = new SerializationOutput(factory1);

        Assertions.assertThatThrownBy(() -> ser.serialize(new C(1)))
                .isInstanceOf(NotSerializableException.class)
                .hasMessage(errMsg("a", getClass().getName()));
    }

}
