package net.corda.example;

import net.corda.core.CordaInternal;

public class WithInternalField {
    @CordaInternal
    public static final String INTERNAL_FIELD = "<secret value>";
}