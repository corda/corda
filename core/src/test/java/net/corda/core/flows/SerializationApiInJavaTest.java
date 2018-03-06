/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows;

import net.corda.core.serialization.SerializationDefaults;
import net.corda.core.serialization.SerializationFactory;
import net.corda.testing.core.SerializationEnvironmentRule;
import org.junit.Rule;
import org.junit.Test;

import static net.corda.core.serialization.SerializationAPIKt.serialize;
import static org.junit.Assert.assertNull;

/**
 * Enforce parts of the serialization API that aren't obvious from looking at the {@link net.corda.core.serialization.SerializationAPIKt} code.
 */
public class SerializationApiInJavaTest {
    @Rule
    public final SerializationEnvironmentRule testSerialization = new SerializationEnvironmentRule();

    @Test
    public void enforceSerializationFactoryApi() {
        assertNull(SerializationFactory.Companion.getCurrentFactory());
        SerializationFactory factory = SerializationFactory.Companion.getDefaultFactory();
        assertNull(factory.getCurrentContext());
        serialize("hello", factory, factory.getDefaultContext());
    }

    @Test
    public void enforceSerializationDefaultsApi() {
        SerializationDefaults defaults = SerializationDefaults.INSTANCE;
        SerializationFactory factory = defaults.getSERIALIZATION_FACTORY();
        serialize("hello", factory, defaults.getP2P_CONTEXT());
        serialize("hello", factory, defaults.getRPC_SERVER_CONTEXT());
        serialize("hello", factory, defaults.getRPC_CLIENT_CONTEXT());
        serialize("hello", factory, defaults.getSTORAGE_CONTEXT());
        serialize("hello", factory, defaults.getCHECKPOINT_CONTEXT());
    }
}
