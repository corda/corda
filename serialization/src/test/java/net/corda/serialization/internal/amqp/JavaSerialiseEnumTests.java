/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp;

import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.junit.Test;

import java.io.NotSerializableException;

import static net.corda.serialization.internal.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;

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

        SerializationOutput ser = new SerializationOutput(testDefaultFactory());
        ser.serialize(bra, TestSerializationContext.testSerializationContext);
    }
}
