/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.*;

public class WhitelistClassLoaderTest {

    private static WhitelistClassLoader wlcl;

    @BeforeClass
    public static void setup() throws Exception {
        TestUtils.setPathToTmpJar("/resource.jar");
    }

    @Before
    public void setupIndividualTest() throws Exception {
        wlcl = WhitelistClassLoader.of(TestUtils.getJarFSRoot());
    }

    @AfterClass
    public static void shutdown() throws Exception {
        TestUtils.cleanupTmpJar();
    }

    @Test
    public void given_ValidBasicMethods_then_ClassCanBeLoaded() throws Exception {
        Class<?> clz = wlcl.loadClass("resource.CallObjectMethods");
        assertNotNull("Loaded class appears to be null", clz);
    }

    @Test(expected = ClassNotFoundException.class)
    public void given_ValidIOMethod_then_ClassCannotBeLoaded() throws Exception {
        Class<?> clz = wlcl.loadClass("resource.CallPrintln");

        fail("Class should not load");
    }

    @Test(expected = ClassNotFoundException.class)
    public void given_InvokeDynamic_then_ClassCannotBeLoaded() throws Exception {
        Class<?> clz = wlcl.loadClass("resource.UseLambdaToForceInvokeDynamic");
        fail("Class should not load");
    }

    @Test
    public void given_BasicCollections_then_ClassCanBeLoaded() throws Exception {
        wlcl.addJarToSandbox(TestUtils.copySandboxJarToTmpDir("/sandbox.jar"));
        final Class<?> clz = wlcl.loadClass("resource.UseBasicCollections");

        assertNotNull("Loaded class appears to be null", clz);
    }

    @Test
    public void given_SimpleLinkedClasses_then_ClassCanBeLoaded() throws Exception {
        Class<?> clz = wlcl.loadClass("resource.ARefersToB");
        assertNotNull("Loaded class appears to be null", clz);
    }

    @Test
    public void given_DeeplyTransitivelyLinkedClasses_then_ClassCanBeLoaded() throws Exception {
        Class<?> clz = wlcl.loadClass("transitive.Chain4701");
        assertNotNull("Loaded class appears to be null", clz);
        final Object o = clz.newInstance();
        assertNotNull("Created object appears to be null", o);
    }

    //TODO This code frequently throws StackOverflowException, despite this being explicitly what the code is trying to prevent!!
    @Ignore
    @Test(expected = ClassNotFoundException.class)
    public void given_OverlyDeeplyTransitivelyLinkedClasses_then_ClassCanBeLoaded() throws Exception {
        Class<?> clz = wlcl.loadClass("transitive.Chain4498");
        fail("Class should not have loaded, but it did");
    }

    @Test
    @Ignore
    public void foo_tesst() throws Exception {
        Class<?> clz = null;
        try {
            clz = wlcl.loadClass("transitive.Chain4498");
        } catch (final Throwable e) {
        }
        System.out.println("Handled first OK");
        assertNull(clz);

        // RESET
        setupIndividualTest();
//        clz = wlcl.loadClass("transitive.Chain4501");
        clz = wlcl.loadClass("transitive.Chain4601");
        assertNotNull("Loaded class appears to be null", clz);
        final Object o = clz.newInstance();
        assertNotNull("Created object appears to be null", o);
    }

    @Test
    public void given_SimpleCyclicClasses_then_ClassCanBeLoaded() throws Exception {
        Class<?> clz = wlcl.loadClass("resource.ARefersToBCyclic");
        assertNotNull("Loaded class appears to be null", clz);
        final Object o = clz.newInstance();
        assertTrue("New object should be a Runnable", o instanceof Runnable);
        Runnable r = (Runnable) o;
        r.run();
        assertTrue("Execution of run failed", true);
    }

    @Test
    public void given_MultipleTransformedClasses_then_ClassCanBeLoaded() throws Exception {
        final Class<?> clz = wlcl.loadClass("resource.ObjectArrayAlloc");
        assertNotNull("ObjectArrayAlloc class could not be transformed and loaded", clz);
        final Object o = clz.newInstance();
        final Method allocObj = clz.getMethod("addEntry");
        final Object ret = allocObj.invoke(o);
        assertTrue(ret instanceof String);
        final String s = (String) ret;
        assertEquals("324Foo", s);
    }

    @Test
    public void test_test_exceptions() throws Exception {
        final Class<?> clz = wlcl.loadClass("resource.ThrowExceptions");
        assertNotNull("ThrowExceptions class could not be transformed and loaded", clz);
    }


    // TODO Test cases that terminate when other resource limits are broken
    @Test
    public void when_too_much_memory_is_allocated_then_thread_dies() throws Exception {
        final Class<?> clz = wlcl.loadClass("resource.LargeByteArrayAlloc");
        final AtomicBoolean executed = new AtomicBoolean(false);

        Runnable r = () -> {
            try {
                final Object o = clz.newInstance();
                final Method allocObj = clz.getMethod("addEntry");
                final Object ret = allocObj.invoke(o);
            } catch (InvocationTargetException invx) {
                return;
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException ex) {
            }
            executed.set(true);
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        };

        Thread t = new Thread(r);
        t.start();
        t.join();
        // Belt and braces - did the thread die before it could flip the AtomicBoolean
        assertFalse("Executed condition should be false", executed.get());
    }

}
