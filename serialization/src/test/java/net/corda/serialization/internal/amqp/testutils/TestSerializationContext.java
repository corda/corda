package net.corda.serialization.internal.amqp.testutils;

import net.corda.core.serialization.SerializationContext;
import net.corda.core.utilities.ByteSequence;
import net.corda.serialization.internal.AllWhitelist;
import net.corda.serialization.internal.SerializationContextImpl;


import java.util.HashMap;
import java.util.Map;

public class TestSerializationContext {

    private static Map<Object, Object> serializationProperties = new HashMap<>();

    public static SerializationContext testSerializationContext = new SerializationContextImpl(
        ByteSequence.of(new byte[] { 'c', 'o', 'r', 'd', 'a', (byte)0, (byte)0, (byte)1}),
        ClassLoader.getSystemClassLoader(),
        AllWhitelist.INSTANCE,
        serializationProperties,
        false,
        SerializationContext.UseCase.Testing,
        null);
}
