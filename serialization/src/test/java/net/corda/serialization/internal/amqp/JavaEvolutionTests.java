package net.corda.serialization.internal.amqp;

import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.AllWhitelist;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaEvolutionTests {
    Path parentDir() {
        try {
            Path dir = Paths.get(this.getClass().getResource("/").toURI());

            for (;;) {
                if (Files.exists(Paths.get(dir.toString(), ".git"))) {
                    return dir;
                }
                dir = dir.getParent();
            }
        } catch (URISyntaxException e) {
            return null;
        }
    }

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

    private Path getPath(String resource) {
        return Paths.get(parentDir().toString(),
                "serialization/src/test/resources/net/corda/serialization/internal/amqp/"
                        + resource);
    }

    SerializerFactory factory = new SerializerFactory(
            AllWhitelist.INSTANCE,
            ClassLoader.getSystemClassLoader(),
            new EvolutionSerializerGetter(),
            new SerializerFingerPrinter());

    @Test
    public void testN1AddsNullableInt() throws IOException {
        String resource = "JavaEvolutionTests.testN1AddsNullableInt";

        // Uncomment to regenerate the base state of the test
        /*
        N1 n = new N1("potato");

        SerializedBytes<Object> bytes = new SerializationOutput(factory).serialize(
                n, TestSerializationContext.testSerializationContext);

        new FileOutputStream(new File(getPath(resource).toUri())).write(bytes.getBytes());
        */

        N1 n2 = new DeserializationInput(factory).deserialize(
                new SerializedBytes<>(Files.readAllBytes(
                        Paths.get(this.getClass().getResource(resource).getPath()))),
                N1.class,
                TestSerializationContext.testSerializationContext);

        assertEquals(n2.getWord(), "potato");
        assertEquals(n2.getWibble(), null);
    }

    @Test
    public void testN2AddsPrimitive() throws IOException {
        String resource = "JavaEvolutionTests.testN2AddsPrimitive";
        String w = "This is only a test";

        // Uncomment to regenerate the base state of the test
        /*
        N2 n = new N2(w);

        SerializedBytes<Object> bytes = new SerializationOutput(factory).serialize(
                n, TestSerializationContext.testSerializationContext);

        new FileOutputStream(new File(getPath(resource).toUri())).write(bytes.getBytes());
        */
        Assertions.assertThatThrownBy(() -> new DeserializationInput(factory).deserialize(
                new SerializedBytes<>(Files.readAllBytes(
                        Paths.get(this.getClass().getResource(resource).getPath()))),
                N2.class,
                TestSerializationContext.testSerializationContext)
        ).isInstanceOf(NotSerializableException.class);

    }
}
