package net.corda.serialization.djvm;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public class InnocentData {
    private final String message;
    private final Short number;

    public InnocentData(String message, Short number) {
        this.message = message;
        this.number = number;
    }

    public String getMessage() {
        return message;
    }

    public Short getNumber() {
        return number;
    }
}
