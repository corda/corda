/* Copyright (c) 2022, Daniel Gredler. All rights reserved. */

package net.gredler.aegis4j;

import static net.gredler.aegis4j.TestUtils.OWNED;

import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Sets a system property upon deserialization, as proof of vulnerability.
 */
public class SerializablePojo implements Serializable {

    private static final long serialVersionUID = -3148228827965096990L;

    private void readObject(ObjectInputStream input) {
        // code executed when object is deserialized
        System.setProperty(OWNED, Boolean.TRUE.toString());
    }
}
