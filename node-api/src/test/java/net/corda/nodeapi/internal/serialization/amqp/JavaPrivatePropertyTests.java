package net.corda.nodeapi.internal.serialization.amqp;

import net.corda.nodeapi.internal.serialization.AllWhitelist;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.NotSerializableException;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public class JavaPrivatePropertyTests {
    static class C {
        private String a;

        C(String a) { this.a = a; }
    }

    static class C2 {
        private String a;

        C2(String a) { this.a = a; }

        public String getA() { return a; }
    }

    @Test
    public void singlePrivateWithConstructor() throws NotSerializableException, NoSuchFieldException, IllegalAccessException {
        EvolutionSerializerGetterBase evolutionSerializerGetter = new EvolutionSerializerGetter();
        SerializerFactory factory = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                evolutionSerializerGetter);
        SerializationOutput ser = new SerializationOutput(factory);
        DeserializationInput des = new DeserializationInput(factory);

        C c = new C("dripping taps");
        C c2 = des.deserialize(ser.serialize(c), C.class);

        assertEquals (c.a, c2.a);

        //
        // Now ensure we actually got a private property serializer
        //
        Field f = SerializerFactory.class.getDeclaredField("serializersByDescriptor");
        f.setAccessible(true);

        ConcurrentHashMap<Object, AMQPSerializer<Object>> serializersByDescriptor =
                (ConcurrentHashMap<Object, AMQPSerializer<Object>>) f.get(factory);

        assertEquals(1, serializersByDescriptor.size());
        ObjectSerializer cSerializer = ((ObjectSerializer)serializersByDescriptor.values().toArray()[0]);
        assertEquals(1, cSerializer.getPropertySerializers().component1().size());
        Object[] propertyReaders = cSerializer.getPropertySerializers().component1().toArray();
        assertTrue (((PropertySerializer)propertyReaders[0]).getPropertyReader() instanceof PrivatePropertyReader);
    }

    @Test
    public void singlePrivateWithConstructorAndGetter()
            throws NotSerializableException, NoSuchFieldException, IllegalAccessException {
        EvolutionSerializerGetterBase evolutionSerializerGetter = new EvolutionSerializerGetter();
        SerializerFactory factory = new SerializerFactory(AllWhitelist.INSTANCE,
                ClassLoader.getSystemClassLoader(), evolutionSerializerGetter);
        SerializationOutput ser = new SerializationOutput(factory);
        DeserializationInput des = new DeserializationInput(factory);

        C2 c = new C2("dripping taps");
        C2 c2 = des.deserialize(ser.serialize(c), C2.class);

        assertEquals (c.a, c2.a);

        //
        // Now ensure we actually got a private property serializer
        //
        Field f = SerializerFactory.class.getDeclaredField("serializersByDescriptor");
        f.setAccessible(true);
        ConcurrentHashMap<Object, AMQPSerializer<Object>> serializersByDescriptor =
                (ConcurrentHashMap<Object, AMQPSerializer<Object>>) f.get(factory);

        assertEquals(1, serializersByDescriptor.size());
        ObjectSerializer cSerializer = ((ObjectSerializer)serializersByDescriptor.values().toArray()[0]);
        assertEquals(1, cSerializer.getPropertySerializers().component1().size());
        Object[] propertyReaders = cSerializer.getPropertySerializers().component1().toArray();
        assertTrue (((PropertySerializer)propertyReaders[0]).getPropertyReader() instanceof PublicPropertyReader);
    }
}
