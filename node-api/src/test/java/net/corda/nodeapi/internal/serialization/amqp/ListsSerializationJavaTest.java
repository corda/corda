package net.corda.nodeapi.internal.serialization.amqp;

import net.corda.core.serialization.CordaSerializable;
import net.corda.core.serialization.SerializedBytes;
import net.corda.nodeapi.internal.serialization.AllWhitelist;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ListsSerializationJavaTest {

    @CordaSerializable
    interface Parent {}

    public static class Child implements Parent {
        private int value;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Child child = (Child) o;

            return value == child.value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    @CordaSerializable
    public static class CovariantContainer<T extends Parent> {
        private List<T> content;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CovariantContainer<T> that = (CovariantContainer<T>) o;

            return content != null ? content.equals(that.content) : that.content == null;
        }

        @Override
        public int hashCode() {
            return content != null ? content.hashCode() : 0;
        }

        public void setContent(List<T> content) {
            this.content = content;
        }

        public List<T> getContent() {
            return content;
        }
    }

    @Test
    public void checkCovariance() throws Exception {
        List<Child> payload = new ArrayList<>();
        Child child1 = new Child();
        child1.setValue(1);
        payload.add(child1);
        Child child2 = new Child();
        child2.setValue(2);
        payload.add(child2);
        CovariantContainer<Child> container = new CovariantContainer<>();
        container.setContent(payload);
        assertEqualAfterRoundTripSerialization(container);
    }

    // Have to have own version as Kotlin inline functions cannot be easily called from Java
    private static void assertEqualAfterRoundTripSerialization(CovariantContainer<Child> container) throws Exception {
        SerializerFactory factory1 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader());
        SerializationOutput ser = new SerializationOutput(factory1);
        SerializedBytes<Object> bytes = ser.serialize(container);
        DeserializationInput des = new DeserializationInput(factory1);
        CovariantContainer deserialized = des.deserialize(bytes, CovariantContainer.class);
        Assert.assertEquals(container, deserialized);
    }
}
