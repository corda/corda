package net.corda.serialization.internal;

import net.corda.core.serialization.*;
import net.corda.core.serialization.internal.CheckpointSerializationContext;
import net.corda.core.serialization.internal.CheckpointSerializationFactory;
import net.corda.node.serialization.kryo.CordaClosureSerializer;
import net.corda.testing.core.SerializationEnvironmentRule;
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

public final class LambdaCheckpointSerializationTest {

    @Rule
    public final CheckpointSerializationEnvironmentRule testCheckpointSerialization =
            new CheckpointSerializationEnvironmentRule();

    private CheckpointSerializationFactory factory;
    private CheckpointSerializationContext context;

    @Before
    public void setup() {
        factory = testCheckpointSerialization.getCheckpointSerializationFactory();
        context = new CheckpointSerializationContextImpl(
                getClass().getClassLoader(),
                AllWhitelist.INSTANCE,
                Collections.emptyMap(),
                true,
                null
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    public final void serialization_works_for_serializable_java_lambdas() throws Exception {
        String value = "Hey";
        Callable<String> target = (Callable<String> & Serializable) () -> value;

        SerializedBytes<Callable<String>> serialized = serialize(target);
        Callable<String> deserialized = deserialize(serialized, Callable.class);

        assertThat(deserialized.call()).isEqualTo(value);
    }

    @Test
    @SuppressWarnings("unchecked")
    public final void serialization_fails_for_not_serializable_java_lambdas() throws Exception {
        String value = "Hey";
        Callable<String> target = () -> value;

        Throwable throwable = catchThrowable(() -> serialize(target));

        assertThat(throwable).isNotNull();
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
        assertThat(throwable).hasMessage(CordaClosureSerializer.ERROR_MESSAGE);
    }

    private <T> SerializedBytes<T> serialize(final T target) {
        return factory.serialize(target, context);
    }

    private <T> T deserialize(final SerializedBytes<? extends T> bytes, final Class<T> type) {
        return factory.deserialize(bytes, type, context);
    }
}
