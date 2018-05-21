/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal;

import com.google.common.collect.Maps;
import net.corda.core.serialization.SerializationContext;
import net.corda.core.serialization.SerializationFactory;
import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.amqp.SchemaKt;
import net.corda.testing.core.SerializationEnvironmentRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.NotSerializableException;
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
            SerializationContext context = new SerializationContextImpl(SchemaKt.getAmqpMagic(),
                    this.getClass().getClassLoader(), AllWhitelist.INSTANCE, Maps.newHashMap(), true, ctx, null);
            String value = "Hey";
            Callable<String> target = (Callable<String> & Serializable) () -> value;

            Throwable throwable = catchThrowable(() -> serialize(target, context));

            assertThat(throwable)
                    .isNotNull()
                    .isInstanceOf(NotSerializableException.class)
                    .hasMessageContaining(getClass().getName());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public final void serialization_fails_for_not_serializable_java_lambdas() {
        contexts.forEach(ctx -> {
            SerializationContext context = new SerializationContextImpl(SchemaKt.getAmqpMagic(),
                    this.getClass().getClassLoader(), AllWhitelist.INSTANCE, Maps.newHashMap(), true, ctx, null);
            String value = "Hey";
            Callable<String> target = () -> value;

            Throwable throwable = catchThrowable(() -> serialize(target, context));

            assertThat(throwable)
                    .isNotNull()
                    .isInstanceOf(NotSerializableException.class)
                    .hasMessageContaining(getClass().getName());
        });
    }

    private <T> SerializedBytes<T> serialize(final T target, final SerializationContext context) {
        return factory.serialize(target, context);
    }
}
