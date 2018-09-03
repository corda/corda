package net.corda.serialization.internal.amqp;

import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.junit.Test;

import java.io.NotSerializableException;

import static net.corda.serialization.internal.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;

public class JavaSerialiseEnumTests {

    public enum Bras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    private static class Bra {
        private final Bras bra;

        private Bra(Bras bra) {
            this.bra = bra;
        }

        public Bras getBra() {
            return this.bra;
        }
    }

    @Test
    public void testJavaConstructorAnnotations() throws NotSerializableException {
        Bra bra = new Bra(Bras.UNDERWIRE);

        SerializationOutput ser = new SerializationOutput(testDefaultFactory());
        ser.serialize(bra, TestSerializationContext.testSerializationContext);
    }
}
