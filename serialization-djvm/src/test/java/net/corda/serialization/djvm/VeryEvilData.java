package net.corda.serialization.djvm;

@SuppressWarnings("unused")
public class VeryEvilData extends InnocentData {
    static {
        if (!VeryEvilData.class.getName().startsWith("sandbox.")) {
            // Execute our evil payload OUTSIDE the sandbox!
            throw new IllegalStateException("Victory is mine!");
        }
    }

    public VeryEvilData(String message, Short number) {
        super(message, number);
    }
}
