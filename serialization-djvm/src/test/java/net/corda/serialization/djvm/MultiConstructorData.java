package net.corda.serialization.djvm;

import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.core.serialization.CordaSerializable;

@SuppressWarnings({"unused", "WeakerAccess"})
@CordaSerializable
public class MultiConstructorData {
    private final String message;
    private final long bigNumber;
    private final Character tag;

    @ConstructorForDeserialization
    public MultiConstructorData(String message, long bigNumber, Character tag) {
        this.message = message;
        this.bigNumber = bigNumber;
        this.tag = tag;
    }

    public MultiConstructorData(String message, long bigNumber) {
        this(message, bigNumber, null);
    }

    public MultiConstructorData(String message, char tag) {
        this(message, 0, tag);
    }

    public MultiConstructorData(String message) {
        this(message, 0);
    }

    public String getMessage() {
        return message;
    }

    public long getBigNumber() {
        return bigNumber;
    }

    public Character getTag() {
        return tag;
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public String toString() {
        return new StringBuilder("MultiConstructor[message='").append(message)
            .append("', bigNumber=").append(bigNumber)
            .append(", tag=").append(tag)
            .append(']')
            .toString();
    }
}
