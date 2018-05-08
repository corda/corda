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

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.nodeapi.internal.serialization.AllWhitelist;
import net.corda.core.serialization.SerializedBytes;
import net.corda.nodeapi.internal.serialization.amqp.testutils.TestSerializationContext;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.io.NotSerializableException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertTrue;

public class JavaSerializationOutputTests {

    static class Foo {
        private final String bob;
        private final int count;

        public Foo(String msg, long count) {
            this.bob = msg;
            this.count = (int) count;
        }

        @ConstructorForDeserialization
        private Foo(String fred, int count) {
            this.bob = fred;
            this.count = count;
        }

        @SuppressWarnings("unused")
        public String getFred() {
            return bob;
        }

        @SuppressWarnings("unused")
        public int getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Foo foo = (Foo) o;

            if (count != foo.count) return false;
            return bob != null ? bob.equals(foo.bob) : foo.bob == null;
        }

        @Override
        public int hashCode() {
            int result = bob != null ? bob.hashCode() : 0;
            result = 31 * result + count;
            return result;
        }
    }

    static class UnAnnotatedFoo {
        private final String bob;
        private final int count;

        private UnAnnotatedFoo(String fred, int count) {
            this.bob = fred;
            this.count = count;
        }

        @SuppressWarnings("unused")
        public String getFred() {
            return bob;
        }

        @SuppressWarnings("unused")
        public int getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UnAnnotatedFoo foo = (UnAnnotatedFoo) o;

            if (count != foo.count) return false;
            return bob != null ? bob.equals(foo.bob) : foo.bob == null;
        }

        @Override
        public int hashCode() {
            int result = bob != null ? bob.hashCode() : 0;
            result = 31 * result + count;
            return result;
        }
    }

    static class BoxedFoo {
        private final String fred;
        private final Integer count;

        private BoxedFoo(String fred, Integer count) {
            this.fred = fred;
            this.count = count;
        }

        @SuppressWarnings("unused")
        public String getFred() {
            return fred;
        }

        @SuppressWarnings("unused")
        public Integer getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BoxedFoo boxedFoo = (BoxedFoo) o;

            if (fred != null ? !fred.equals(boxedFoo.fred) : boxedFoo.fred != null) return false;
            return count != null ? count.equals(boxedFoo.count) : boxedFoo.count == null;
        }

        @Override
        public int hashCode() {
            int result = fred != null ? fred.hashCode() : 0;
            result = 31 * result + (count != null ? count.hashCode() : 0);
            return result;
        }
    }


    static class BoxedFooNotNull {
        private final String fred;
        private final Integer count;

        private BoxedFooNotNull(String fred, Integer count) {
            this.fred = fred;
            this.count = count;
        }

        public String getFred() {
            return fred;
        }

        @Nonnull
        public Integer getCount() {
            return count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BoxedFooNotNull boxedFoo = (BoxedFooNotNull) o;

            if (fred != null ? !fred.equals(boxedFoo.fred) : boxedFoo.fred != null) return false;
            return count != null ? count.equals(boxedFoo.count) : boxedFoo.count == null;
        }

        @Override
        public int hashCode() {
            int result = fred != null ? fred.hashCode() : 0;
            result = 31 * result + (count != null ? count.hashCode() : 0);
            return result;
        }
    }

    private Object serdes(Object obj) throws NotSerializableException {
        EvolutionSerializerGetterBase evolutionSerialiserGetter = new EvolutionSerializerGetter();
        FingerPrinter fingerPrinter = new SerializerFingerPrinter();
        SerializerFactory factory1 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                evolutionSerialiserGetter,
                fingerPrinter);
        SerializerFactory factory2 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                evolutionSerialiserGetter,
                fingerPrinter);
        SerializationOutput ser = new SerializationOutput(factory1);
        SerializedBytes<Object> bytes = ser.serialize(obj, TestSerializationContext.testSerializationContext);

        DecoderImpl decoder = new DecoderImpl();

        decoder.register(Envelope.Companion.getDESCRIPTOR(), Envelope.Companion);
        decoder.register(Schema.Companion.getDESCRIPTOR(), Schema.Companion);
        decoder.register(Descriptor.Companion.getDESCRIPTOR(), Descriptor.Companion);
        decoder.register(Field.Companion.getDESCRIPTOR(), Field.Companion);
        decoder.register(CompositeType.Companion.getDESCRIPTOR(), CompositeType.Companion);
        decoder.register(Choice.Companion.getDESCRIPTOR(), Choice.Companion);
        decoder.register(RestrictedType.Companion.getDESCRIPTOR(), RestrictedType.Companion);
        decoder.register(Transform.Companion.getDESCRIPTOR(), Transform.Companion);
        decoder.register(TransformsSchema.Companion.getDESCRIPTOR(), TransformsSchema.Companion);

        new EncoderImpl(decoder);
        decoder.setByteBuffer(ByteBuffer.wrap(bytes.getBytes(), 8, bytes.getSize() - 8));
        Envelope result = (Envelope) decoder.readObject();
        assertTrue(result != null);

        DeserializationInput des = new DeserializationInput(factory2);
        Object desObj = des.deserialize(bytes, Object.class, TestSerializationContext.testSerializationContext);
        assertTrue(Objects.deepEquals(obj, desObj));

        // Now repeat with a re-used factory
        SerializationOutput ser2 = new SerializationOutput(factory1);
        DeserializationInput des2 = new DeserializationInput(factory1);
        Object desObj2 = des2.deserialize(ser2.serialize(obj, TestSerializationContext.testSerializationContext),
                Object.class, TestSerializationContext.testSerializationContext);

        assertTrue(Objects.deepEquals(obj, desObj2));
        // TODO: check schema is as expected
        return desObj2;
    }

    @Test
    public void testJavaConstructorAnnotations() throws NotSerializableException {
        Foo obj = new Foo("Hello World!", 123);
        serdes(obj);
    }

    @Test
    public void testJavaConstructorWithoutAnnotations() throws NotSerializableException {
        UnAnnotatedFoo obj = new UnAnnotatedFoo("Hello World!", 123);
        serdes(obj);
    }


    @Test
    public void testBoxedTypes() throws NotSerializableException {
        BoxedFoo obj = new BoxedFoo("Hello World!", 123);
        serdes(obj);
    }

    @Test
    public void testBoxedTypesNotNull() throws NotSerializableException {
        BoxedFooNotNull obj = new BoxedFooNotNull("Hello World!", 123);
        serdes(obj);
    }
}
