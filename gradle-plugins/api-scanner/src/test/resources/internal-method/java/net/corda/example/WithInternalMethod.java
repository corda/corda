package net.corda.example;

import net.corda.core.CordaInternal;

public class WithInternalMethod {
    @CordaInternal
    public void internalMethod() {
        System.out.println("INTERNAL METHOD");
    }
}