package net.corda.serialization.internal.carpenter;

import net.corda.core.serialization.SerializableCalculatedProperty;
import net.corda.core.serialization.SerializationContext;
import net.corda.core.serialization.SerializationFactory;
import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.AllWhitelist;
import net.corda.serialization.internal.amqp.*;
import net.corda.serialization.internal.amqp.Schema;
import net.corda.testing.core.SerializationEnvironmentRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static net.corda.serialization.internal.amqp.testutils.AMQPTestUtilsKt.testDefaultFactoryNoEvolution;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JavaCalculatedValuesToClassCarpenterTest extends AmqpCarpenterBase {
    public JavaCalculatedValuesToClassCarpenterTest() {
        super(AllWhitelist.INSTANCE);
    }

    public interface Parent {
        @SerializableCalculatedProperty
        int getDoubled();
    }

    public static final class C implements Parent {
        private final int i;

        public C(int i) {
            this.i = i;
        }

        @SerializableCalculatedProperty
        public String getSquared() {
            return Integer.toString(i * i);
        }

        @Override
        public int getDoubled() {
            return i * 2;
        }

        public int getI() {
            return i;
        }
    }

    @Rule
    public final SerializationEnvironmentRule serializationEnvironmentRule = new SerializationEnvironmentRule();
    private SerializationContext context;

    @Before
    public void initSerialization() {
        SerializationFactory factory = serializationEnvironmentRule.getSerializationFactory();
        context = factory.getDefaultContext();
    }

    @Test
    public void calculatedValues() throws Exception {
        SerializerFactory factory = testDefaultFactoryNoEvolution();
        SerializedBytes<C> serialized = serialise(new C(2));
        ObjectAndEnvelope<C> objAndEnv = new DeserializationInput(factory)
                .deserializeAndReturnEnvelope(serialized, C.class, context);

        C amqpObj = objAndEnv.getObj();
        Schema schema = objAndEnv.getEnvelope().getSchema();

        assertEquals(2, amqpObj.getI());
        assertEquals("4", amqpObj.getSquared());
        assertEquals(2, schema.getTypes().size());
        assertTrue(schema.getTypes().get(0) instanceof CompositeType);

        CompositeType concrete = (CompositeType) schema.getTypes().get(0);
        assertEquals(3, concrete.getFields().size());
        assertEquals("doubled", concrete.getFields().get(0).getName());
        assertEquals("int", concrete.getFields().get(0).getType());
        assertEquals("i", concrete.getFields().get(1).getName());
        assertEquals("int", concrete.getFields().get(1).getType());
        assertEquals("squared", concrete.getFields().get(2).getName());
        assertEquals("string", concrete.getFields().get(2).getType());

        assertEquals(0, AMQPSchemaExtensions.carpenterSchema(schema, ClassLoader.getSystemClassLoader()).getSize());
        Schema mangledSchema = ClassCarpenterTestUtilsKt.mangleNames(schema, singletonList(C.class.getTypeName()));
        CarpenterMetaSchema l2 = AMQPSchemaExtensions.carpenterSchema(mangledSchema, ClassLoader.getSystemClassLoader());
        String mangledClassName = ClassCarpenterTestUtilsKt.mangleName(C.class.getTypeName());

        assertEquals(1, l2.getSize());
        net.corda.serialization.internal.carpenter.Schema carpenterSchema = l2.getCarpenterSchemas().stream()
                .filter(s -> s.getName().equals(mangledClassName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No schema found for mangled class name " + mangledClassName));

        Class<?> pinochio = new ClassCarpenterImpl(AllWhitelist.INSTANCE).build(carpenterSchema);
        Object p = pinochio.getConstructors()[0].newInstance(4, 2, "4");

        assertEquals(pinochio.getMethod("getI").invoke(p), amqpObj.getI());
        assertEquals(pinochio.getMethod("getSquared").invoke(p), amqpObj.getSquared());
        assertEquals(pinochio.getMethod("getDoubled").invoke(p), amqpObj.getDoubled());

        Parent upcast = (Parent) p;
        assertEquals(upcast.getDoubled(), amqpObj.getDoubled());
    }
}
