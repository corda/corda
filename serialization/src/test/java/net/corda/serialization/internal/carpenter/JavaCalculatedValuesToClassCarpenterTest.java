package net.corda.serialization.internal.carpenter;

import net.corda.core.serialization.SerializableCalculatedProperty;
import net.corda.core.serialization.SerializationContext;
import net.corda.core.serialization.SerializationFactory;
import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.AllWhitelist;
import net.corda.serialization.internal.amqp.*;
import net.corda.serialization.internal.amqp.Schema;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import net.corda.serialization.internal.model.RemoteTypeInformation;
import net.corda.serialization.internal.model.TypeIdentifier;
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

        TypeIdentifier typeToMangle = TypeIdentifier.Companion.forClass(C.class);
        Envelope env = objAndEnv.getEnvelope();
        RemoteTypeInformation typeInformation = getTypeInformation(env).values().stream()
                .filter(it -> it.getTypeIdentifier().equals(typeToMangle))
                .findFirst()
                .orElseThrow(IllegalStateException::new);

        RemoteTypeInformation renamed = rename(typeInformation, typeToMangle, mangle(typeToMangle));

        Class<?> pinochio = load(renamed, TestSerializationContext.testSerializationContext);
        Object p = pinochio.getConstructors()[0].newInstance(4, 2, "4");

        assertEquals(2, pinochio.getMethod("getI").invoke(p));
        assertEquals("4", pinochio.getMethod("getSquared").invoke(p));
        assertEquals(4, pinochio.getMethod("getDoubled").invoke(p));

        Parent upcast = (Parent) p;
        assertEquals(4, upcast.getDoubled());
    }
}
