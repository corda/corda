package net.corda.nodeapi.internal.serialization;

import com.google.common.collect.Maps;
import net.corda.core.serialization.SerializationContext;
import net.corda.core.serialization.SerializationDefaults;
import net.corda.core.serialization.SerializationFactory;
import net.corda.core.serialization.SerializedBytes;
import net.corda.testing.TestDependencyInjectionBase;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

public final class LambdaCheckpointSerializationTest extends TestDependencyInjectionBase {

    private SerializationFactory factory;
    private SerializationContext context;

    @Before
    public void setup() {
        factory = SerializationDefaults.INSTANCE.getSERIALIZATION_FACTORY();
        context = new SerializationContextImpl(SerializationSchemeKt.getKryoHeaderV0_1(), this.getClass().getClassLoader(), AllWhitelist.INSTANCE, Maps.newHashMap(), true, SerializationContext.UseCase.Checkpoint);
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
        assertThat(throwable).hasMessage(CordaClosureSerializer.INSTANCE.getERROR_MESSAGE());
    }

    private <T> SerializedBytes<T> serialize(final T target) {
        return factory.serialize(target, context);
    }

    private <T> T deserialize(final SerializedBytes<? extends T> bytes, final Class<T> type) {
        return factory.deserialize(bytes, type, context);
    }
}
