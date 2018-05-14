package net.corda.nodeapi.internal.serialization;

import com.google.common.collect.Maps;
import net.corda.core.serialization.SerializationContext;
import net.corda.core.serialization.SerializationFactory;
import net.corda.core.serialization.SerializedBytes;
import net.corda.nodeapi.internal.serialization.kryo.CordaClosureBlacklistSerializer;
import net.corda.nodeapi.internal.serialization.kryo.KryoSerializationSchemeKt;
import net.corda.testing.core.SerializationEnvironmentRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

public final class ForbiddenLambdaSerializationTests {
    private EnumSet<SerializationContext.UseCase> contexts = EnumSet.complementOf(
            EnumSet.of(SerializationContext.UseCase.Checkpoint, SerializationContext.UseCase.Testing));
    @Rule
    public final SerializationEnvironmentRule testSerialization = new SerializationEnvironmentRule();
    private SerializationFactory factory;

    @Before
    public void setup() {
        factory = testSerialization.getSerializationFactory();
    }

    @Test
    public final void serialization_fails_for_serializable_java_lambdas() {
        contexts.forEach(ctx -> {
            SerializationContext context = new SerializationContextImpl(KryoSerializationSchemeKt.getKryoMagic(),
                    this.getClass().getClassLoader(), AllWhitelist.INSTANCE, Maps.newHashMap(), true, ctx, null);
            String value = "Hey";
            Callable<String> target = (Callable<String> & Serializable) () -> value;

            Throwable throwable = catchThrowable(() -> serialize(target, context));

            assertThat(throwable).isNotNull();
            assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
            if (ctx != SerializationContext.UseCase.RPCServer && ctx != SerializationContext.UseCase.Storage) {
                assertThat(throwable).hasMessage(CordaClosureBlacklistSerializer.ERROR_MESSAGE);
            } else {
                assertThat(throwable).hasMessageContaining("RPC not allowed to deserialise internal classes");
            }
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public final void serialization_fails_for_not_serializable_java_lambdas() {
        contexts.forEach(ctx -> {
            SerializationContext context = new SerializationContextImpl(KryoSerializationSchemeKt.getKryoMagic(),
                    this.getClass().getClassLoader(), AllWhitelist.INSTANCE, Maps.newHashMap(), true, ctx, null);
            String value = "Hey";
            Callable<String> target = () -> value;

            Throwable throwable = catchThrowable(() -> serialize(target, context));

            assertThat(throwable).isNotNull();
            assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
            assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
            if (ctx != SerializationContext.UseCase.RPCServer && ctx != SerializationContext.UseCase.Storage) {
                assertThat(throwable).hasMessage(CordaClosureBlacklistSerializer.ERROR_MESSAGE);
            } else {
                assertThat(throwable).hasMessageContaining("RPC not allowed to deserialise internal classes");
            }
        });
    }

    private <T> SerializedBytes<T> serialize(final T target, final SerializationContext context) {
        return factory.serialize(target, context);
    }
}
