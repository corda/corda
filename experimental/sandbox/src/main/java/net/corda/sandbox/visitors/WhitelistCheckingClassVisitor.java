/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox.visitors;

import net.corda.sandbox.WhitelistClassLoader;
import net.corda.sandbox.CandidacyStatus;

import java.util.Arrays;

import net.corda.sandbox.CandidateMethod;
import net.corda.sandbox.Utils;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.Opcodes.*;

/**
 * A ASM ClassVisitor which checks classes it visits against a whitelist
 */
public final class WhitelistCheckingClassVisitor extends ClassVisitor {

    private final CandidacyStatus candidacyStatus;
    private final String classname;
    private final Set<String> internalMethodNames = new HashSet<>();
    private String currentClassName;

    private static final Logger LOGGER = LoggerFactory.getLogger(WhitelistCheckingClassVisitor.class);

    public WhitelistCheckingClassVisitor(final String currentClass, final CandidacyStatus initialCandidacyStatus) {
        super(Opcodes.ASM5);
        candidacyStatus = initialCandidacyStatus;
        classname = currentClass;
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        currentClassName = name;

        if (resolveState(Utils.convertInternalFormToQualifiedClassName(superName)) == CandidateMethod.State.DISALLOWED) {
            candidacyStatus.setLoadable(false);
            candidacyStatus.setReason("Superclass " + superName + " could not be loaded");
            return;
        }

        for (final String interfaceName : interfaces) {
            if (resolveState(Utils.convertInternalFormToQualifiedClassName(interfaceName)) == CandidateMethod.State.DISALLOWED) {
                candidacyStatus.setLoadable(false);
                candidacyStatus.setReason("Interface " + interfaceName + " could not be loaded");
                return;
            }
        }
    }

    /**
     * We initially take the method passed in and store an internal representation of
     * the method signature in the our CandidacyStatus working set.
     * <p>
     * We then get an ASM MethodVisitor (which can read the byte code of the method) and pass that to our
     * custom method visitor which perform additional checks.
     *
     * @param access
     * @param name
     * @param desc
     * @param signature
     * @param exceptions
     * @return
     */
    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Visiting method with: access [" + access + "], name [" + currentClassName + "::" + name + "], signature [" + signature + "], desc ["
                    + desc + "] and exceptions [" + Arrays.toString(exceptions) + "]");

        // Force new access control flags - for now just strictfp for deterministic
        // compliance to IEEE 754
        final int maskedAccess = access | ACC_STRICT;

        final String internalName = classname + "." + name + ":" + desc;
        internalMethodNames.add(internalName);
        candidacyStatus.putIfAbsent(internalName);

        final MethodVisitor baseMethodVisitor = super.visitMethod(maskedAccess, name, desc, signature, exceptions);

        // If we're already not allowed to be loaded (a CandidateMethod was disallowed) 
        // no other MethodVisitor can help
        // so return a MethodVisitor that doesn't even try to do any more work
        if (!candidacyStatus.isLoadable()) {
            // This can mask problems with the class deeper down, so disabled for now for debugging
//            return new DefinitelyDisallowedMethodVisitor(baseMethodVisitor);
        }

        // Disallow finalizers
        if ("finalize".equals(name) && "()V".equals(desc)) {
            return new DefinitelyDisallowedMethodVisitor(baseMethodVisitor);
        }

        // Native methods are completely disallowed
        if ((access & Opcodes.ACC_NATIVE) > 0) {
            candidacyStatus.setLoadable(false);
            candidacyStatus.setReason("Method " + internalName + " is native");
            return new DefinitelyDisallowedMethodVisitor(baseMethodVisitor);
        }

        return new WhitelistCheckingMethodVisitor(baseMethodVisitor, candidacyStatus, internalName);
    }

    /**
     * Once we've finished visiting all of the methods, we check that they're all deterministic, if not we
     * tell the candidacyStatus that this is not loadable and why.
     */
    @Override
    public void visitEnd() {
        if (!candidacyStatus.isLoadable())
            return;
        METHODS:
        for (String internalMethodName : internalMethodNames) {
            final CandidateMethod candidateMethod = candidacyStatus.getCandidateMethod(internalMethodName);
            final CandidateMethod.State candidateState = candidateMethod.getCurrentState();

            switch (candidateState) {
                case DISALLOWED:
                    candidacyStatus.setLoadable(false);
                    candidacyStatus.setReason(candidateMethod.getReason());
                    break METHODS;
                case DETERMINISTIC:
                    break;
                case MENTIONED:
                case SCANNED:
                    // Try a recursive scan (to allow multiple classes to be loaded
                    // as part of the same call). The scan needs to happen on the
                    // methods we *refer* to, not the current method
                    for (final CandidateMethod referred : candidateMethod.getReferencedCandidateMethods()) {
                        final String internalName = referred.getInternalMethodName();

                        final String toLoadQualified = Utils.convertInternalMethodNameToQualifiedClassName(internalName);
                        if (!Utils.shouldAttemptToTransitivelyLoad(toLoadQualified)
                                || resolveState(toLoadQualified) == CandidateMethod.State.DISALLOWED) {
                            referred.disallowed(internalName + " is DISALLOWED");
                            candidacyStatus.setLoadable(false);
                            candidacyStatus.setReason(candidateMethod.getReason());
                            break METHODS;
                        }
                    }
                    candidateMethod.deterministic();
                    break;
            }
        }
    }

    /**
     * Take the name of a class and attempts to load it using a WLCL.
     *
     * @param qualifiedClassname
     * @return
     */
    CandidateMethod.State resolveState(final String qualifiedClassname) {
        Class<?> clz = null;
        try {
            candidacyStatus.incRecursiveCount();
            final ClassLoader loader = WhitelistClassLoader.of(candidacyStatus.getContextLoader());
            clz = loader.loadClass(qualifiedClassname);
            candidacyStatus.decRecursiveCount();
        } catch (ClassNotFoundException ex) {
            return CandidateMethod.State.DISALLOWED;
        }
        if (clz == null) {
            LOGGER.error("Couldn't load: " + qualifiedClassname);
            return CandidateMethod.State.DISALLOWED;
        }

        return CandidateMethod.State.DETERMINISTIC;
    }

    public CandidacyStatus getCandidacyStatus() {
        return candidacyStatus;
    }

    public Set<String> getInternalMethodNames() {
        return internalMethodNames;
    }
}
