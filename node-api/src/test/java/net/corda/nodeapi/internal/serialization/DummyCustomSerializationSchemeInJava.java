package net.corda.nodeapi.internal.serialization;

import net.corda.core.serialization.SerializationSchemeContext;
import net.corda.core.serialization.CustomSerializationScheme;
import net.corda.core.serialization.SerializedBytes;
import net.corda.core.utilities.ByteSequence;

public class DummyCustomSerializationSchemeInJava implements CustomSerializationScheme {

        public class DummyOutput {}

        static final int testMagic = 7;

        @Override
        public int getSchemeId() {
            return testMagic;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T deserialize(ByteSequence bytes, Class<T> clazz, SerializationSchemeContext context) {
            return (T)new DummyOutput();
        }

        @Override
        public <T> SerializedBytes<T> serialize(T obj, SerializationSchemeContext context) {
            byte[] myBytes = {0xA, 0xA};
            return new SerializedBytes<>(myBytes);
        }
}