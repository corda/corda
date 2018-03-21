package net.corda.example;

import java.io.InputStream;
import java.io.FilterInputStream;

public class ExtendedClass extends FilterInputStream {
    public ExtendedClass(InputStream input) {
        super(input);
    }
}
