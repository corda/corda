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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ben
 */
public final class Utils {

    public static final String SANDBOX_PREFIX_INTERNAL = "sandbox/";

    public static final String CLASSFILE_NAME_SUFFIX = "^(.*)\\.class$";

    public static final Pattern JAVA_LANG_PATTERN_INTERNAL = Pattern.compile("^java/lang/(.*)");

    public static final Pattern SANDBOX_PATTERN_INTERNAL = Pattern.compile("^" + SANDBOX_PREFIX_INTERNAL + "(.*)");

    public static final Pattern SIGNATURE_PATTERN_INTERNAL = Pattern.compile("\\((.*)\\)(.+)");

    public static final Pattern REFTYPE_PATTERN_INTERNAL = Pattern.compile("(L[^;]+;)");

    public static final Pattern ARRAY_REFTYPE_PATTERN_INTERNAL = Pattern.compile("((\\[+)L[^;]+;)");

    public static final Pattern JAVA_PATTERN_QUALIFIED = Pattern.compile("^java\\.(.+)");

    public static final Pattern CLASSNAME_PATTERN_QUALIFIED = Pattern.compile("([^\\.]+)\\.");

    public static final String OBJECT = "java/lang/Object";

    public static final String THROWABLE = "java/lang/Throwable";

    public static final String ERROR = "java/lang/Error";

    public static final String THREAD_DEATH = "java/lang/ThreadDeath";

    // Hide constructor
    private Utils() {
    }

    /**
     * Helper method that converts from the internal class name format (as used in the
     * Constant Pool) to a fully-qualified class name. No obvious library method to do this
     * appears to exist, hence this code. If one exists, rip this out.
     *
     * @param classInternalName
     * @return
     */
    public static String convertInternalFormToQualifiedClassName(final String classInternalName) {
        String out = classInternalName.replaceAll("/", "\\.");
        return out;
    }

    /**
     * This method takes in an internal method name but needs to return a qualified
     * classname (suitable for loading)
     *
     * @param internalMethodName
     * @return
     */
    public static String convertInternalMethodNameToQualifiedClassName(final String internalMethodName) {
        final Matcher classMatch = CLASSNAME_PATTERN_QUALIFIED.matcher(internalMethodName);
        if (classMatch.find()) {
            return convertInternalFormToQualifiedClassName(classMatch.group(1));
        } else {
            throw new IllegalArgumentException(internalMethodName + " is not a legal method name");
        }
    }

    /**
     * Helper method that converts from a fully-qualified class name to the internal class
     * name format (as used in the Constant Pool). No obvious library method to do this
     * appears to exist, hence this code. If one exists, rip this out.
     *
     * @param qualifiedClassName
     * @return
     */
    public static String convertQualifiedClassNameToInternalForm(final String qualifiedClassName) {
        String out = qualifiedClassName.replaceAll("\\.", "/");
        return out;
    }

    /**
     * This method potentially rewrites the classname.
     *
     * @param internalClassname - specified in internal form
     * @return
     */
    public static String sandboxInternalTypeName(final String internalClassname) {
        if (classShouldBeSandboxedInternal(internalClassname)) {
            final Matcher arrayMatch = ARRAY_REFTYPE_PATTERN_INTERNAL.matcher(internalClassname);
            if (arrayMatch.find()) {
                final String indirection = arrayMatch.group(2);
                return indirection + SANDBOX_PREFIX_INTERNAL + internalClassname.substring(indirection.length());
            } else {
                // Regular, non-array reftype
                return SANDBOX_PREFIX_INTERNAL + internalClassname;
            }
        }

        return internalClassname;
    }

    /**
     * @param qualifiedTypeName
     * @return
     */
    public static String sandboxQualifiedTypeName(final String qualifiedTypeName) {
        final String internal = convertQualifiedClassNameToInternalForm(qualifiedTypeName);
        final String sandboxedInternal = sandboxInternalTypeName(internal);
        if (internal.equals(sandboxedInternal)) {
            return qualifiedTypeName;
        }
        return convertInternalFormToQualifiedClassName(sandboxedInternal);
    }

    /**
     * This method removes the sandboxing prefix from a method or type name, if it has
     * one, otherwise it returns the input string.
     *
     * @param internalClassname
     * @return the internal classname, unsandboxed if that was required
     */
    public static String unsandboxNameIfNeedBe(final String internalClassname) {
        final Matcher m = SANDBOX_PATTERN_INTERNAL.matcher(internalClassname);
        if (m.find()) {
            return m.group(1);
        }
        return internalClassname;
    }

    /**
     * @param desc - internal
     * @return the rewritten desc string
     */
    public static String rewriteDescInternal(final String desc) {
        String remaining = desc;
        final Matcher formatCheck = SIGNATURE_PATTERN_INTERNAL.matcher(desc);
        // Check it's a valid signature string
        if (!formatCheck.find())
            return remaining;

        final StringBuilder out = new StringBuilder();
        while (!remaining.isEmpty()) {
            final Matcher refTypeFound = REFTYPE_PATTERN_INTERNAL.matcher(remaining);
            if (refTypeFound.find()) {
                final int startOfType = refTypeFound.start();
                final int endOfType = refTypeFound.end();
                final String before = remaining.substring(0, startOfType);

                final String found = refTypeFound.group(1);
                final String rewritten = "L" + sandboxInternalTypeName(found.substring(1));
                out.append(before);
                out.append(rewritten);
                remaining = remaining.substring(endOfType);
            } else {
                out.append(remaining);
                remaining = "";
            }
        }

        return out.toString();
    }

    /**
     * Determines whether a classname in qualified form is a candidate for transitive
     * loading. This should not attempt to load a classname that starts with java. as
     * the only permissable classes have already been transformed into sandboxed
     * methods
     *
     * @param qualifiedClassName
     * @return
     */
    public static boolean shouldAttemptToTransitivelyLoad(final String qualifiedClassName) {
        return !JAVA_PATTERN_QUALIFIED.asPredicate().test(qualifiedClassName);
    }

    /**
     * Helper method that determines whether this class requires sandboxing
     *
     * @param clazzName - specified in internal form
     * @return true if the class should be sandboxed
     */
    public static boolean classShouldBeSandboxedInternal(final String clazzName) {
        if (ARRAY_REFTYPE_PATTERN_INTERNAL.asPredicate().test(clazzName)) {
            return classShouldBeSandboxedInternal(clazzName.substring(2, clazzName.length() - 1));
        }

        if (JAVA_LANG_PATTERN_INTERNAL.asPredicate().test(clazzName)) {
            return false;
        }

        return !SANDBOX_PATTERN_INTERNAL.asPredicate().test(clazzName);
    }


}
