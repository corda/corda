package net.corda.serialization.internal.amqp;

import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.AllWhitelist;
import net.corda.serialization.internal.amqp.testutils.AMQPTestUtilsKt;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JavaEvolutionTests {
    @Rule
    public final ExpectedException exception = ExpectedException.none();

    // Class as it was when it was serialized and written to disk. Uncomment
    // if the test referencing the object needs regenerating.
    /*
    static class N1 {
        private String word;
        public N1(String word) { this.word = word; }
        public String getWord() { return word; }
    }
    */
    // Class as it exists now with the newly added element
    static class N1 {
        private String word;
        private Integer wibble;

        public N1(String word, Integer wibble) {
            this.word = word;
            this.wibble = wibble;
        }
        public String getWord() { return word; }
        public Integer getWibble() { return wibble; }
    }

    // Class as it was when it was serialized and written to disk. Uncomment
    // if the test referencing the object needs regenerating.
    /*
    static class N2 {
        private String word;
        public N2(String word) { this.word = word; }
        public String getWord() { return word; }
    }
    */

    // Class as it exists now with the newly added element
    static class N2 {
        private String word;
        private float wibble;

        public N2(String word, float wibble) {
            this.word = word;
            this.wibble = wibble;
        }
        public String getWord() { return word; }
        public float getWibble() { return wibble; }
    }

    SerializerFactory factory = new SerializerFactory(
            AllWhitelist.INSTANCE,
            new ClassCarpenterImpl(AllWhitelist.INSTANCE),
            new EvolutionSerializerGetter(),
            new SerializerFingerPrinter());

    @Test
    public void testN1AddsNullableInt() throws IOException {
        // Uncomment to regenerate the base state of the test
        /*
        N1 n = new N1("potato");
        AMQPTestUtilsKt.writeTestResource(this, new SerializationOutput(factory).serialize(
                n, TestSerializationContext.testSerializationContext));
        */

        N1 n2 = new DeserializationInput(factory).deserialize(
                new SerializedBytes<>(AMQPTestUtilsKt.readTestResource(this)),
                N1.class,
                TestSerializationContext.testSerializationContext);
        assertEquals(n2.getWord(), "potato");
        assertNull(n2.getWibble());
    }

    @Test
    public void testN2AddsPrimitive() throws IOException {
        // Uncomment to regenerate the base state of the test
        /*
        N2 n = new N2("This is only a test");

        AMQPTestUtilsKt.writeTestResource(this, new SerializationOutput(factory).serialize(
                n, TestSerializationContext.testSerializationContext));
        */

        exception.expect(NotSerializableException.class);
        new DeserializationInput(factory).deserialize(
                new SerializedBytes<>(AMQPTestUtilsKt.readTestResource(this)),
                N2.class,
                TestSerializationContext.testSerializationContext);
    }
}
