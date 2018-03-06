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

import net.corda.sandbox.TestUtils;

import static net.corda.sandbox.TestUtils.*;

import net.corda.sandbox.Utils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;

import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.BeforeClass;

/**
 * @author ben
 */
public class SandboxedRewritingTest {

    @Before
    public void setup() {
        RuntimeCostAccounter.resetCounters();
    }

    @BeforeClass
    public static void setup_resource_jar() throws IOException, URISyntaxException {
        TestUtils.setPathToTmpJar("/resource.jar");
    }

    @AfterClass
    public static void kill_resource_jar() throws IOException {
        TestUtils.cleanupTmpJar();
    }

    @Test
    public void testRewriting() {
        String rewritten = Utils.sandboxInternalTypeName("java/lang/Object");
        assertEquals("Expected Object::equals to be unchanged, but it was: " + rewritten, "java/lang/Object", rewritten);
        rewritten = Utils.sandboxInternalTypeName("java/util/ArrayList");
        assertEquals("Expected ArrayList::new to be sandboxed, but it was: " + rewritten, "sandbox/java/util/ArrayList", rewritten);
        rewritten = Utils.sandboxInternalTypeName("sandbox/java/util/ArrayList");
        assertEquals("Expected sandboxed ArrayList::new to be left unchanged, but it was: " + rewritten, "sandbox/java/util/ArrayList", rewritten);
    }

    @Test
    public void when_desc_is_provided_it_is_correctly_rewritten() {
        final String voidSig = "()V";
        final String rwVoidSig = Utils.rewriteDescInternal(voidSig);
        assertEquals("Expected " + voidSig + " to be unchanged, but it was: " + rwVoidSig, rwVoidSig, voidSig);

        final String primSig = "(IIJ)Z";
        final String rwPrimSig = Utils.rewriteDescInternal(primSig);
        assertEquals("Expected " + primSig + " to be unchanged, but it was: " + rwPrimSig, rwPrimSig, primSig);

        final String toStringSig = "()Ljava/lang/String;";
        final String rwToStringSig = Utils.rewriteDescInternal(toStringSig);
        assertEquals("Expected " + toStringSig + " to be unchanged, but it was: " + rwToStringSig, rwToStringSig, toStringSig);

        final String listGetterSig = "()Ljava/util/ArrayList;";
        final String exListGetterSig = "()Lsandbox/java/util/ArrayList;";
        final String rwListGetterSig = Utils.rewriteDescInternal(listGetterSig);
        assertEquals("Expected " + listGetterSig + " to be " + exListGetterSig + ", but it was: " + rwListGetterSig, exListGetterSig, rwListGetterSig);

        final String sandboxListGetterSig = "()Lsandbox/java/util/ArrayList;";
        final String rwSandboxListGetterSig = Utils.rewriteDescInternal(sandboxListGetterSig);
        assertEquals("Expected " + sandboxListGetterSig + " to be unchanged, but it was: " + rwSandboxListGetterSig, sandboxListGetterSig, rwSandboxListGetterSig);

        final String twoSig = "(Ljava/util/HashMap;)Ljava/util/Set;";
        final String exTwoSig = "(Lsandbox/java/util/HashMap;)Lsandbox/java/util/Set;";
        final String rwTwoSig = Utils.rewriteDescInternal(twoSig);
        assertEquals("Expected " + twoSig + " to be " + exTwoSig + ", but it was: " + rwTwoSig, exTwoSig, rwTwoSig);

        final String arrSig = "(Ljava/util/HashMap;)[Ljava/util/Set;";
        final String exArrSig = "(Lsandbox/java/util/HashMap;)[Lsandbox/java/util/Set;";
        final String rwArrSig = Utils.rewriteDescInternal(arrSig);
        assertEquals("Expected " + arrSig + " to be " + exArrSig + ", but it was: " + rwArrSig, exArrSig, rwArrSig);

        final String compArrSig = "([[IJLjava/util/HashMap;)[[Ljava/util/Set;";
        final String exCompArrSig = "([[IJLsandbox/java/util/HashMap;)[[Lsandbox/java/util/Set;";
        final String rwCompArrSig = Utils.rewriteDescInternal(compArrSig);
        assertEquals("Expected " + compArrSig + " to be " + exCompArrSig + ", but it was: " + rwCompArrSig, exCompArrSig, rwCompArrSig);
    }

    @Test
    public void actually_rewrite_a_method_access_and_check_it_works_as_expected() throws Exception {
        final Class<?> clz = transformClass("resource/MethodAccessIsRewritten.class", 412);
        final String className = clz.getName();
        assertEquals("Incorrect rewritten class name: ", "sandbox.resource.MethodAccessIsRewritten", className);
        final Object o = clz.newInstance();
        final Method m = clz.getMethod("makeObject");
        final Object ret = m.invoke(o);
        assertTrue(Object.class == ret.getClass());
        checkAllCosts(1, 0, 2, 0);
    }

    @Test
    public void actually_rewrite_calls_to_object_methods() throws Exception {
        final Class<?> clz = transformClass("resource/CallObjectMethods.class", 525);
        final String className = clz.getName();
        assertEquals("Incorrect rewritten class name: ", "sandbox.resource.CallObjectMethods", className);
        final Object o = clz.newInstance();
        final Method m = clz.getMethod("callBasicMethodsOnObject");
        final Object ret = m.invoke(o);
        assertTrue(Boolean.class == ret.getClass());
        assertTrue((Boolean) ret);
        checkAllCosts(0, 2, 3, 0);
    }

    @Test
    public void check_primitive_array_allocation() throws Exception {
        final Class<?> clz = transformClass("resource/SimpleArrayAlloc.class", 727);
        final String className = clz.getName();
        assertEquals("Incorrect rewritten class name: ", "sandbox.resource.SimpleArrayAlloc", className);
        final Method m = clz.getMethod("allocPrimitiveArrays");
        final Object ret = m.invoke(null);
        assertNull(ret);
        checkAllCosts(778, 1, 0, 0);
    }

}
