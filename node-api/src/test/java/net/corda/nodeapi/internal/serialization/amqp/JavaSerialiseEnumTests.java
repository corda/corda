/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp;

import org.junit.Test;

import net.corda.nodeapi.internal.serialization.AllWhitelist;
import net.corda.core.serialization.SerializedBytes;

import java.io.NotSerializableException;

public class JavaSerialiseEnumTests {

    public enum Bras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    private static class Bra {
        private final Bras bra;

        private Bra(Bras bra) {
            this.bra = bra;
        }

        public Bras getBra() {
            return this.bra;
        }
    }

    @Test
    public void testJavaConstructorAnnotations() throws NotSerializableException {
        Bra bra = new Bra(Bras.UNDERWIRE);

        SerializerFactory factory1 = new SerializerFactory(AllWhitelist.INSTANCE, ClassLoader.getSystemClassLoader(),
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter());
        SerializationOutput ser = new SerializationOutput(factory1);
        SerializedBytes<Object> bytes = ser.serialize(bra);
    }
}
