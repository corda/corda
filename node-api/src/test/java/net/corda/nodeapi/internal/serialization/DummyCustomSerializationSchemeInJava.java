package net.corda.nodeapi.internal.serialization;

import net.corda.core.serialization.CustomSerializationContext;
import net.corda.core.serialization.CustomSerializationScheme;
import net.corda.core.serialization.SerializedBytes;

public class DummyCustomSerializationSchemeInJava implements CustomSerializationScheme {

        public class DummyOutput {}

        static final int testMagic = 7;

        @Override
        public int getSchemeId() {
            return testMagic;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T deserialize(SerializedBytes<T> bytes, Class<T> clazz, CustomSerializationContext context) {
            return (T)new DummyOutput();
        }

        @Override
        public <T> SerializedBytes<T> serialize(T obj, CustomSerializationContext context) {
            byte[] myBytes = {0xA, 0xA};
            return new SerializedBytes<>(myBytes);
        }
}