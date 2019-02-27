package net.corda.serialization.internal.amqp;

import kotlin.Suppress;
import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.amqp.testutils.AMQPTestUtilsKt;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
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
    @SuppressWarnings("unused")
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

    SerializerFactory factory = AMQPTestUtilsKt.testDefaultFactory();

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

    // Class as it was when it was serialized and written to disk. Uncomment
    // if the test referencing the object needs regenerating.
    /*
    @SuppressWarnings("unused")
    static class POJOWithInteger {
        private Integer id;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }
    */

    /*
     We want to force the evolution serializer factory to check that the property types of the local and
     remote types match up, which only happens if both types have the same set of property names (i.e.
     this might be a spurious evolution candidate). We do this by adding a marker interface to the type,
     which will change its fingerprint but have no effect on its serialisation behaviour.
    */
    public interface ForceEvolution { }

    // Class as it exists now with the newly added interface
    @SuppressWarnings("unused")
    static class POJOWithInteger implements ForceEvolution {
        private Integer id;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }

    @Test
    public void testNullableInteger() throws IOException {
        // Uncomment to regenerate the base state of the test
        //POJOWithInteger n = new POJOWithInteger();
        //n.setId(100);
        //AMQPTestUtilsKt.writeTestResource(this, new SerializationOutput(factory).serialize(
        //        n, TestSerializationContext.testSerializationContext));

        POJOWithInteger n2 = new DeserializationInput(factory).deserialize(
                new SerializedBytes<>(AMQPTestUtilsKt.readTestResource(this)),
                POJOWithInteger.class,
                TestSerializationContext.testSerializationContext);

        assertEquals(Integer.valueOf(100), n2.getId());
    }
}
