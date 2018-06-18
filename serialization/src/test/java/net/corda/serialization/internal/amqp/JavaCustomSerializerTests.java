package net.corda.serialization.internal.amqp;

import net.corda.core.serialization.SerializationCustomSerializer;
import net.corda.serialization.internal.AllWhitelist;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.junit.Test;

import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.List;

public class JavaCustomSerializerTests {
    /**
     * The class lacks a public constructor that takes parameters it can associate
     * with its properties and is thus not serializable by the CORDA serialization
     * framework.
     */
    static class Example {
        private Integer a;
        private Integer b;

        Integer getA() { return  a; }
        Integer getB() { return  b; }

        public Example(List<Integer> l) {
            this.a = l.get(0);
            this.b = l.get(1);
        }
    }

    /**
     * This is the class that will Proxy instances of Example within the serializer
     */
    public static class ExampleProxy {
        /**
         * These properties will be serialized into the byte stream, this is where we choose how to
         * represent instances of the object we're proxying. In this example, which is somewhat
         * contrived, this choice is obvious. In your own classes / 3rd party libraries, however, this
         * may require more thought.
         */
        private Integer proxiedA;
        private Integer proxiedB;

        /**
         * The proxu class itself must be serializable by the framework, it must thus have a constructor that
         * can be mapped to the properties of the class via getter methods.
         */
        public Integer getProxiedA() { return proxiedA; }
        public Integer getProxiedB() { return  proxiedB; }


        public ExampleProxy(Integer proxiedA, Integer proxiedB) {
            this.proxiedA = proxiedA;
            this.proxiedB = proxiedB;
        }
    }

    /**
     * Finally this is the custom serializer that will automatically loaded into the serialization
     * framework when the CorDapp Jar is scanned at runtime.
     */
    public static class ExampleSerializer implements SerializationCustomSerializer<Example, ExampleProxy> {

        /**
         *  Given an instance of the Example class, create an instance of the proxying object ExampleProxy.
         *
         *  Essentially convert Example -> ExampleProxy
         */
        public ExampleProxy toProxy(Example obj) {
            return new ExampleProxy(obj.getA(), obj.getB());
        }

        /**
         * Conversely, given an instance of the proxy object, revert that back to an instance of the
         * type being proxied.
         *
         *  Essentially convert ExampleProxy -> Example
         *
         */
        public Example fromProxy(ExampleProxy proxy) {
            List<Integer> l = new ArrayList<Integer>(2);
            l.add(proxy.getProxiedA());
            l.add(proxy.getProxiedB());
            return new Example(l);
        }

    }

    @Test
    public void serializeExample() throws NotSerializableException, NoSuchFieldException, IllegalAccessException {
        SerializerFactory factory = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());
        SerializationOutput ser = new SerializationOutput(factory);

        List<Integer> l = new ArrayList<Integer>(2);
        l.add(10);
        l.add(20);
        Example e = new Example(l);

        CorDappCustomSerializer ccs = new CorDappCustomSerializer(new ExampleSerializer(), factory);
        factory.registerExternal(ccs);

        ser.serialize(e, TestSerializationContext.testSerializationContext);


    }
}
