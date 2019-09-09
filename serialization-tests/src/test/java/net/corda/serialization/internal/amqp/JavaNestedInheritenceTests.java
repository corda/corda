package net.corda.serialization.internal.amqp;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.NotSerializableException;
import java.util.List;

import static net.corda.serialization.internal.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class JavaNestedInheritenceTestsBase {
    class DummyState implements ContractState {
        @Override @NotNull public List<AbstractParty> getParticipants() {
            return ImmutableList.of();
        }
    }
}

class Wrapper {
    private ContractState cs;
    Wrapper(ContractState cs) { this.cs = cs; }
}

class TemplateWrapper<T> {
    public T obj;
    TemplateWrapper(T obj) { this.obj = obj; }
}

public class JavaNestedInheritenceTests extends JavaNestedInheritenceTestsBase {
    @Test
    public void serializeIt() {
        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);

        assertThatThrownBy(() -> ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void serializeIt2() {
        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);
        assertThatThrownBy(() -> ser.serialize(new Wrapper (new DummyState()), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void serializeIt3() {
        SerializerFactory factory1 = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory1);

        assertThatThrownBy(() -> ser.serialize(new TemplateWrapper<ContractState> (new DummyState()), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }
}
