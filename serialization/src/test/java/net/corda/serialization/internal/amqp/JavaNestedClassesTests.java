package net.corda.serialization.internal.amqp;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.AllWhitelist;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.NotSerializableException;
import java.util.List;

class OuterClass1 {
    protected SerializationOutput ser;
    DeserializationInput desExisting;
    DeserializationInput desRegen;

    OuterClass1() {
        SerializerFactory factory1 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        SerializerFactory factory2 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        this.ser = new SerializationOutput(factory1);
        this.desExisting = new DeserializationInput(factory1);
        this.desRegen = new DeserializationInput(factory2);
    }

    class DummyState implements ContractState {
        @Override @NotNull public List<AbstractParty> getParticipants() {
            return ImmutableList.of();
        }
    }

    public void run() throws NotSerializableException {
        SerializedBytes b = ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext);
        desExisting.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
        desRegen.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
    }
}

class Inherator1 extends OuterClass1 {
    public void iRun() throws NotSerializableException {
        SerializedBytes b = ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext);
        desExisting.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
        desRegen.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
    }
}

class OuterClass2 {
    protected SerializationOutput ser;
    DeserializationInput desExisting;
    DeserializationInput desRegen;

    OuterClass2() {
        SerializerFactory factory1 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        SerializerFactory factory2 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        this.ser = new SerializationOutput(factory1);
        this.desExisting = new DeserializationInput(factory1);
        this.desRegen = new DeserializationInput(factory2);
    }

    protected class DummyState implements ContractState {
        private Integer count;

        DummyState(Integer count) {
            this.count = count;
        }

         @Override @NotNull public List<AbstractParty> getParticipants() {
            return ImmutableList.of();
        }
    }

    public void run() throws NotSerializableException {
        SerializedBytes b = ser.serialize(new DummyState(12), TestSerializationContext.testSerializationContext);
        desExisting.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
        desRegen.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
    }
}

class Inherator2 extends OuterClass2 {
    public void iRun() throws NotSerializableException {
        SerializedBytes b = ser.serialize(new DummyState(12), TestSerializationContext.testSerializationContext);
        desExisting.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
        desRegen.deserialize(b, DummyState.class, TestSerializationContext.testSerializationContext);
    }
}

// Make the base class abstract
abstract class AbstractClass2 {
    protected SerializationOutput ser;

    AbstractClass2() {
        SerializerFactory factory = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        this.ser = new SerializationOutput(factory);
    }

    protected class DummyState implements ContractState {
        @Override @NotNull public List<AbstractParty> getParticipants() {
            return ImmutableList.of();
        }
    }
}

class Inherator4 extends AbstractClass2 {
    public void run() throws NotSerializableException {
        ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext);
    }
}

abstract class AbstractClass3 {
    protected class DummyState implements ContractState {
        @Override @NotNull public List<AbstractParty> getParticipants() {
            return ImmutableList.of();
        }
    }
}

class Inherator5 extends AbstractClass3 {
    public void run() throws NotSerializableException {
        SerializerFactory factory = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        SerializationOutput ser = new SerializationOutput(factory);
        ser.serialize(new DummyState(), TestSerializationContext.testSerializationContext);
    }
}

class Inherator6 extends AbstractClass3 {
    public class Wrapper {
        //@Suppress("UnusedDeclaration"])
        private ContractState cState;

        Wrapper(ContractState cState) {
            this.cState = cState;
        }
    }

    public void run() throws NotSerializableException {
        SerializerFactory factory = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());

        SerializationOutput ser = new SerializationOutput(factory);
        ser.serialize(new Wrapper(new DummyState()), TestSerializationContext.testSerializationContext);
    }
}

public class JavaNestedClassesTests {
    @Test
    public void publicNested() {
        Assertions.assertThatThrownBy(() -> new OuterClass1().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void privateNested() {
        Assertions.assertThatThrownBy(() -> new OuterClass2().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void publicNestedInherited() {
        Assertions.assertThatThrownBy(() -> new Inherator1().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");

        Assertions.assertThatThrownBy(() -> new Inherator1().iRun()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void protectedNestedInherited() {
        Assertions.assertThatThrownBy(() -> new Inherator2().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");

        Assertions.assertThatThrownBy(() -> new Inherator2().iRun()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void abstractNested() {
        Assertions.assertThatThrownBy(() -> new Inherator4().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void abstractNestedFactoryOnNested() {
        Assertions.assertThatThrownBy(() -> new Inherator5().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }

    @Test
    public void abstractNestedFactoryOnNestedInWrapper() {
        Assertions.assertThatThrownBy(() -> new Inherator6().run()).isInstanceOf(
                NotSerializableException.class).hasMessageContaining(
                "has synthetic fields and is likely a nested inner class");
    }
}

