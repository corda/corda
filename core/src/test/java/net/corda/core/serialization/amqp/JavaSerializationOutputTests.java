package net.corda.core.serialization.amqp;

import net.corda.core.serialization.SerializedBytes;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.junit.Test;

import java.io.NotSerializableException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static org.junit.Assert.assertTrue;

public class JavaSerializationOutputTests {

    static class Foo {
        private final String bob;
        private final int count;

        @CordaConstructor
        public Foo(@CordaParam("fred") String msg, @CordaParam("count") int count) {
            this.bob = msg;
            this.count = count;
        }

        public String getFred() {
            return bob;
        }

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

    private Object serdes(Object obj) throws NotSerializableException {
        SerializerFactory factory = new SerializerFactory();
        SerializationOutput ser = new SerializationOutput(factory);
        SerializedBytes<Object> bytes = ser.serialize(obj);

        DecoderImpl decoder = new DecoderImpl();

        decoder.register(Envelope.Companion.getDESCRIPTOR(), Envelope.Constructor.INSTANCE);
        decoder.register(Schema.Companion.getDESCRIPTOR(), Schema.Constructor.INSTANCE);
        decoder.register(Descriptor.Companion.getDESCRIPTOR(), Descriptor.Constructor.INSTANCE);
        decoder.register(Field.Companion.getDESCRIPTOR(), Field.Constructor.INSTANCE);
        decoder.register(CompositeType.Companion.getDESCRIPTOR(), CompositeType.Constructor.INSTANCE);
        decoder.register(Choice.Companion.getDESCRIPTOR(), Choice.Constructor.INSTANCE);
        decoder.register(RestrictedType.Companion.getDESCRIPTOR(), RestrictedType.Constructor.INSTANCE);

        new EncoderImpl(decoder);
        decoder.setByteBuffer(ByteBuffer.wrap(bytes.getBytes(), 8, bytes.getSize() - 8));
        Object result = decoder.readObject();
        System.out.println(result);

        DeserializationInput des = new DeserializationInput();
        Object desObj = des.deserialize(bytes, Object.class);
        System.out.println(desObj);
        assertTrue(Objects.deepEquals(obj, desObj));

        // Now repeat with a re-used factory
        SerializationOutput ser2 = new SerializationOutput(factory);
        DeserializationInput des2 = new DeserializationInput(factory);
        Object desObj2 = des2.deserialize(ser2.serialize(obj), Object.class);
        assertTrue(Objects.deepEquals(obj, desObj2));
        return desObj2;
    }

    @Test
    public void testFoo() throws NotSerializableException {
        Foo obj = new Foo("Hello World!", 123);
        serdes(obj);
    }

}
