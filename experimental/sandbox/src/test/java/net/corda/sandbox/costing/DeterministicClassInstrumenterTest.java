/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox.costing;

import net.corda.sandbox.*;
import org.junit.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author ben
 */
public class DeterministicClassInstrumenterTest {

    @BeforeClass
    public static void setup_resource_jar() throws IOException, URISyntaxException {
        TestUtils.setPathToTmpJar("/resource.jar");
    }

    @AfterClass
    public static void kill_resource_jar() throws IOException {
        TestUtils.cleanupTmpJar();
    }

    @Test
    public void when_given_simple_code_it_executes() throws Exception {
        final Class<?> clz = TestUtils.transformClass("resource/CallObjectMethods.class", 525, 731);
        final Object o = clz.newInstance();
        final Method allocObj = clz.getMethod("callBasicMethodsOnObject");
        final Object ret = allocObj.invoke(o);
        assertTrue(ret instanceof Boolean);
        final Boolean s = (Boolean) ret;
        assertTrue(s);

    }

    @Test
    public void when_monitors_are_present_then_they_are_removed() throws Exception {
        Class<?> clz = TestUtils.transformClass("resource/SynchronizedBlock.class", 522, 720);
        Object o = clz.newInstance();
        Method allocObj = clz.getMethod("exampleBlockSynchronized");
        Object ret = allocObj.invoke(o);
        assertEquals("Synched", ret);

        clz = TestUtils.transformClass("resource/SynchronizedMethod.class", 420, 593);
        o = clz.newInstance();
        allocObj = clz.getMethod("exampleSynchronized");
        ret = allocObj.invoke(o);
        assertEquals("SynchedMethod", ret);
    }

    @Test
    public void when_monitors_are_present_then_byte_stream_is_altered() throws Exception {
        // Do an actual byte check
        byte[] basic = TestUtils.getBytes("resource/SynchronizedBlock.class");
        final byte[] tfmd = TestUtils.instrumentWithCosts(basic, new HashSet<>());

        // -62 is really 0xc2 but signed bytes in Java :(
        final byte[] originalSeq = {0x2a, 0x59, 0x4c, -62, 0x12, 0x02, 0x2b, -61};
        final byte[] tfmdSeq = {0x2a, 0x59, 0x4c, 0x57, 0x12, 0x02, 0x2b, 0x57};

//        TestUtils.printBytes(basic);
        final int origOffset = TestUtils.findOffset(basic, originalSeq);
        final int tmfdOffset = TestUtils.findOffset(tfmd, tfmdSeq);

        for (int i = 0; i < originalSeq.length; i++) {
            assertEquals(originalSeq[i], basic[origOffset + i]);
            assertEquals(tfmdSeq[i], tfmd[tmfdOffset + i]);
        }
    }
}
