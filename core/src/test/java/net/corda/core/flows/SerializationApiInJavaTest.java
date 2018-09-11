package net.corda.core.flows;

import net.corda.core.serialization.SerializationDefaults;
import net.corda.core.serialization.SerializationFactory;
import net.corda.core.serialization.internal.CheckpointSerializationDefaults;
import net.corda.testing.core.SerializationEnvironmentRule;
import org.junit.Rule;
import org.junit.Test;

import static net.corda.core.serialization.internal.CheckpointSerializationAPIKt.checkpointSerialize;
import static net.corda.core.serialization.SerializationAPIKt.serialize;
import static org.junit.Assert.assertNull;

/**
 * Enforce parts of the serialization API that aren't obvious from looking at the {@link net.corda.core.serialization.SerializationAPIKt} code.
 */
public class SerializationApiInJavaTest {
    @Rule
    public final SerializationEnvironmentRule testSerialization = new SerializationEnvironmentRule();

    @Test
    public void enforceSerializationFactoryApi() {
        SerializationFactory factory = SerializationFactory.Companion.getDefaultFactory();
        assertNull(factory.getCurrentContext());
        serialize("hello", factory, factory.getDefaultContext());
    }

    @Test
    public void enforceSerializationDefaultsApi() {
        SerializationDefaults defaults = SerializationDefaults.INSTANCE;
        SerializationFactory factory = defaults.getSERIALIZATION_FACTORY();

        CheckpointSerializationDefaults checkpointDefaults = CheckpointSerializationDefaults.INSTANCE;
        serialize("hello", factory, defaults.getP2P_CONTEXT());
        serialize("hello", factory, defaults.getRPC_SERVER_CONTEXT());
        serialize("hello", factory, defaults.getRPC_CLIENT_CONTEXT());
        serialize("hello", factory, defaults.getSTORAGE_CONTEXT());
        checkpointSerialize("hello", checkpointDefaults.getCHECKPOINT_CONTEXT());
    }
}
