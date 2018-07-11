package net.corda.serialization.internal.amqp;

import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.junit.Test;
import java.io.NotSerializableException;

import static net.corda.serialization.internal.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.jgroups.util.Util.assertEquals;

public class JavaGenericsTest {
    private static class Inner {
        private final Integer v;

        private Inner(Integer v) { this.v = v; }
        Integer getV() { return v; }
    }

    private static class A<T> {
        private final T t;

        private A(T t) { this.t = t; }
        public T getT() { return t; }
    }

    @Test
    public void basicGeneric() throws NotSerializableException {
        A a1 = new A<>(1);

        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);
        SerializedBytes<?> bytes = ser.serialize(a1, TestSerializationContext.testSerializationContext);

        DeserializationInput des = new DeserializationInput(factory);
        A a2 = des.deserialize(bytes, A.class, TestSerializationContext.testSerializationContext);

        assertEquals(1, a2.getT());
    }

    private SerializedBytes<?> forceWildcardSerialize(A<?> a) throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        return (new SerializationOutput(factory)).serialize(a, TestSerializationContext.testSerializationContext);
    }

    private SerializedBytes<?> forceWildcardSerializeFactory(
            A<?> a,
            SerializerFactory factory) throws NotSerializableException {
        return (new SerializationOutput(factory)).serialize(a, TestSerializationContext.testSerializationContext);
    }

    private A<?> forceWildcardDeserialize(SerializedBytes<?> bytes) throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        DeserializationInput des = new DeserializationInput(factory);
        return des.deserialize(bytes, A.class, TestSerializationContext.testSerializationContext);
    }

    private A<?> forceWildcardDeserializeFactory(
            SerializedBytes<?> bytes,
            SerializerFactory factory) throws NotSerializableException {
        return (new DeserializationInput(factory)).deserialize(bytes, A.class,
                TestSerializationContext.testSerializationContext);
    }

    @Test
    public void forceWildcard() throws NotSerializableException {
        SerializedBytes<?> bytes = forceWildcardSerialize(new A<>(new Inner(29)));
        Inner i = (Inner)forceWildcardDeserialize(bytes).getT();
        assertEquals(29, i.getV());
    }

    @Test
    public void forceWildcardSharedFactory() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        SerializedBytes<?> bytes = forceWildcardSerializeFactory(new A<>(new Inner(29)), factory);
        Inner i = (Inner)forceWildcardDeserializeFactory(bytes, factory).getT();

        assertEquals(29, i.getV());
    }
}
