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

import java.util.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents the status of the candidacy of a particular set of candidate methods, i.e. Their progress from
 * being {@link CandidateMethod.State#UNDETERMINED} to {@link CandidateMethod.State#DETERMINISTIC}
 * or {@link CandidateMethod.State#DISALLOWED} states.
 * A method is identified by a string that is encoded as a standard JVM representation
 * as per the constant pool. E.g.: java/lang/Byte.compareTo:(Ljava/lang/Byte;)I
 */
public class CandidacyStatus {

    private static final int MAX_CLASSLOADING_RECURSIVE_DEPTH = 500;

    private static final String DETERMINISTIC_METHODS = "java8.scan.java.lang_and_util"; //"java8.scan.java.lang

    private final SortedMap<String, CandidateMethod> candidateMethods = new TreeMap<>();

    // Backlog of methodSignatures that may have come in from other loaded classes
    private final Set<String> backlog = new LinkedHashSet<>();

    private WhitelistClassLoader contextLoader;

    // Loadable is true by default as it's easier to prove falsehood than truth
    private boolean loadable = true;

    // If at all possible, we want to be able to provide a precise reason why this
    // class is not loadable. As some methods are determined to be showstoppers only
    // at the ClassVisitor it makes sense to store it here (for final reporting)
    // as well as in the CandidateMethod
    private WhitelistClassloadingException reason;

    private int recursiveDepth = 0;

    private CandidacyStatus() {
    }

    /**
     * @param signature
     * @return true if the input was absent from the underlying map
     */
    void putIfAbsent(final String signature, final CandidateMethod candidate) {
        candidateMethods.putIfAbsent(signature, candidate);
    }

    /**
     * @param methodSignature
     * @return true if the input was absent from the underlying map
     */
    public boolean putIfAbsent(final String methodSignature) {
        return null == candidateMethods.putIfAbsent(methodSignature, CandidateMethod.of(methodSignature));
    }

    /**
     * Static factory method
     *
     * @param startingSet
     * @return a candidacy status based on the starting set
     */
    public static CandidacyStatus of(final String startingSet) {
        final CandidacyStatus baseCandidacyStatus = new CandidacyStatus();
        try {
            for (String s : baseCandidacyStatus.readLinesFromFile(startingSet)) {
                baseCandidacyStatus.putIfAbsent(s, CandidateMethod.proven(s));
            }
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }

        return baseCandidacyStatus;
    }

    /**
     * Static factory method
     *
     * @return a candidacy status based on the starting set
     */
    public static CandidacyStatus of() {
        return CandidacyStatus.of(DETERMINISTIC_METHODS);
    }

    /**
     * Add additional methods that are known to be deterministic
     *
     * @param methodNames
     */
    public void addKnownDeterministicMethods(final Set<String> methodNames) {
        for (String known : methodNames) {
            candidateMethods.putIfAbsent(known, CandidateMethod.proven(known));
        }
    }

    /**
     * Getter method for candidate methods
     *
     * @param methodSignature
     * @return the candidate method corresponding to a method signature
     */
    public CandidateMethod getCandidateMethod(final String methodSignature) {
        return candidateMethods.get(methodSignature);
    }

    public Map<String, CandidateMethod> getCandidateMethods() {
        return candidateMethods;
    }

    public void addToBacklog(final String discoveredMethod) {
        if (!backlog.contains(discoveredMethod)) {
            backlog.add(discoveredMethod);
        }
    }

    public List<String> readLinesFromFile(final String fName) throws IOException, URISyntaxException {
        final Path p = Paths.get(getClass().getClassLoader().getResource(fName).toURI());
        return Files.readAllLines(p);
    }

    public boolean isLoadable() {
        return loadable;
    }

    public void setLoadable(final boolean loadable) {
        this.loadable = loadable;
    }

    public WhitelistClassloadingException getReason() {
        return reason;
    }

    public void setReason(final String because) {
        reason = new WhitelistClassloadingException(because);
    }

    public WhitelistClassLoader getContextLoader() {
        return contextLoader;
    }

    public void setContextLoader(final WhitelistClassLoader contextLoader) {
        this.contextLoader = contextLoader;
    }

    /**
     * Increases the recursive depth of this classloading process, throwing a
     * ClassNotFoundException if it becomes too high
     *
     * @throws ClassNotFoundException
     */
    public void incRecursiveCount() throws ClassNotFoundException {
        if (recursiveDepth >= MAX_CLASSLOADING_RECURSIVE_DEPTH - 1) {
            reason = new WhitelistClassloadingException("Recursive depth of classloading exceeded");
            throw new ClassNotFoundException("Class cannot be loaded due to deep recursion", reason);
        }
        recursiveDepth++;
    }

    public void decRecursiveCount() {
        recursiveDepth--;
    }

    public Set<String> getDisallowedMethods() {
        final Set<String> out = new HashSet<>();
        for (final String candidateName : candidateMethods.keySet()) {
            final CandidateMethod candidate = candidateMethods.get(candidateName);
            if (candidate.getCurrentState() == CandidateMethod.State.DISALLOWED) {
                out.add(candidateName);
            }
        }

        return out;
    }

    @Override
    public String toString() {
        return "CandidacyStatus{" + "candidateMethods=" + candidateMethods + ", backlog=" + backlog + ", contextLoader=" + contextLoader + ", loadable=" + loadable + ", reason=" + reason + ", recursiveDepth=" + recursiveDepth + '}';
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.candidateMethods);
        hash = 53 * hash + Objects.hashCode(this.backlog);
        hash = 53 * hash + Objects.hashCode(this.contextLoader);
        hash = 53 * hash + (this.loadable ? 1 : 0);
        hash = 53 * hash + Objects.hashCode(this.reason);
        hash = 53 * hash + this.recursiveDepth;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final CandidacyStatus other = (CandidacyStatus) obj;
        if (!Objects.equals(this.candidateMethods, other.candidateMethods))
            return false;
        if (!Objects.equals(this.backlog, other.backlog))
            return false;
        if (!Objects.equals(this.contextLoader, other.contextLoader))
            return false;
        if (this.loadable != other.loadable)
            return false;
        if (!Objects.equals(this.reason, other.reason))
            return false;
        return this.recursiveDepth == other.recursiveDepth;
    }
}
