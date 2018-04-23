/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
    interface Parent {
    }

    public static class Child implements Parent {
        private final int value;

        Child(int value) {
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

        CovariantContainer(List<T> content) {
            this.content = content;
        }

        @Override
        @SuppressWarnings("unchecked")
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

    @CordaSerializable
    public static class CovariantContainer2 {
        private final List<? extends Parent> content;

        CovariantContainer2(List<? extends Parent> content) {
            this.content = content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CovariantContainer2 that = (CovariantContainer2) o;

            return content != null ? content.equals(that.content) : that.content == null;
        }

        @Override
        public int hashCode() {
            return content != null ? content.hashCode() : 0;
        }

        // Needed to show that there is a property called "content"
        @SuppressWarnings("unused")
        public List<? extends Parent> getContent() {
            return content;
        }
    }

    @Test
    public void checkCovariance() throws Exception {
        List<Child> payload = new ArrayList<>();
        payload.add(new Child(1));
        payload.add(new Child(2));
        CovariantContainer<Child> container = new CovariantContainer<>(payload);
        assertEqualAfterRoundTripSerialization(container, CovariantContainer.class);
    }

    @Test
    public void checkCovariance2() throws Exception {
        List<Child> payload = new ArrayList<>();
        payload.add(new Child(1));
        payload.add(new Child(2));
        CovariantContainer2 container = new CovariantContainer2(payload);
        assertEqualAfterRoundTripSerialization(container, CovariantContainer2.class);
    }

    // Have to have own version as Kotlin inline functions cannot be easily called from Java
    private static <T> void assertEqualAfterRoundTripSerialization(T container, Class<T> clazz) throws Exception {
        EvolutionSerializerGetterBase evolutionSerializerGetter = new EvolutionSerializerGetter();
        FingerPrinter fingerPrinter = new SerializerFingerPrinter();
        SerializerFactory factory1 = new SerializerFactory(
                AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                evolutionSerializerGetter,
                fingerPrinter);
        SerializationOutput ser = new SerializationOutput(factory1);
        SerializedBytes<Object> bytes = ser.serialize(container);
        DeserializationInput des = new DeserializationInput(factory1);
        T deserialized = des.deserialize(bytes, clazz);
        Assert.assertEquals(container, deserialized);
    }
}