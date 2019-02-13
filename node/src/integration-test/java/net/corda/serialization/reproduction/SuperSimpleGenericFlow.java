package net.corda.serialization.reproduction;

import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.serialization.CordaSerializable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@StartableByRPC
public class SuperSimpleGenericFlow extends FlowLogic<GenericHolder<String>> {
    public SuperSimpleGenericFlow() {
    }

    @Override
    public GenericHolder<String> call() {
        return new GenericHolder<>(IntStream.of(100).mapToObj((i) -> "" + i).collect(Collectors.toList()));
    }
}

@CordaSerializable
class GenericHolder<S> {
    private final List<S> items;

    GenericHolder(List<S> items) {
        this.items = items;
    }

    public List<S> getItems() {
        return items;
    }
}
