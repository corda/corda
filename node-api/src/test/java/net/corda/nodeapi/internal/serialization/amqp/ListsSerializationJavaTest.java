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
        private final int value;

        public Child(int value) {
            this.value = value;
        }

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

        // Needed to show that there is a property called "value"
        @SuppressWarnings("unused")
        public int getValue() {
            return value;
        }
    }

    @CordaSerializable
    public static class CovariantContainer<T extends Parent> {
        private final List<T> content;

        public CovariantContainer(List<T> content) {
            this.content = content;
        }

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

        // Needed to show that there is a property called "content"
        @SuppressWarnings("unused")
        public List<T> getContent() {
            return content;
        }
    }

    @Test
    public void checkCovariance() throws Exception {
        List<Child> payload = new ArrayList<>();
        payload.add(new Child(1));
        payload.add(new Child(2));
        CovariantContainer<Child> container = new CovariantContainer<>(payload);
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
