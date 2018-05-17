package net.corda.serialization.internal.amqp;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.serialization.internal.AllWhitelist;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.NotSerializableException;
import java.util.List;

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
        SerializerFactory factory = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        SerializationOutput ser = new SerializationOutput(factory);

        Assertions.assertThatThrownBy(() -> ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void serializeIt2() {
        SerializerFactory factory = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        SerializationOutput ser = new SerializationOutput(factory);
        Assertions.assertThatThrownBy(() -> ser.serialize(new Wrapper (new DummyState()), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void serializeIt3() throws NotSerializableException {
        SerializerFactory factory1 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        SerializationOutput ser = new SerializationOutput(factory1);

        Assertions.assertThatThrownBy(() -> ser.serialize(new TemplateWrapper<ContractState> (new DummyState()), TestSerializationContext.testSerializationContext)).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }
}
