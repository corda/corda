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

import net.corda.sandbox.visitors.CostInstrumentingMethodVisitor;
import net.corda.sandbox.visitors.WhitelistCheckingClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ben
 */
public final class WhitelistClassLoader extends ClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhitelistClassLoader.class);

    private final Map<String, Class<?>> loadedClasses;

    private final Map<String, byte[]> transformedClasses;

    private final List<Path> primaryClasspathSearchPath = new ArrayList<>();

    private final List<Path> fileSystemSearchPath = new ArrayList<>();

    private final CandidacyStatus candidacyStatus;

    private final boolean removeNonDeterministicMethods;

    private Path classDir;

    private String classInternalName;

    private Path outputJarPath;

    private WhitelistClassLoader(final boolean stripNonDeterministicMethods) {
        candidacyStatus = CandidacyStatus.of();
        loadedClasses = new HashMap<>();
        transformedClasses = new HashMap<>();
        removeNonDeterministicMethods = stripNonDeterministicMethods;
    }

    /* 
     * Copy constructor for use in recursive calls
     * @param other 
     */
    private WhitelistClassLoader(WhitelistClassLoader other) {
        candidacyStatus = other.candidacyStatus;
        loadedClasses = other.loadedClasses;
        transformedClasses = other.transformedClasses;
        fileSystemSearchPath.addAll(other.fileSystemSearchPath);
        primaryClasspathSearchPath.addAll(other.primaryClasspathSearchPath);
        removeNonDeterministicMethods = other.removeNonDeterministicMethods;
    }

    /**
     * Static factory method. Throws URISyntaxException currently, as this method is
     * called with user data, so a checked exception is not unreasonable. Could use a
     * runtime exception instead.
     *
     * @param auxiliaryClassPath
     * @param stripNonDeterministic if set to true, then rather than requiring all
     *                              methods to be deterministic, instead the classloader
     *                              will remove all non-deterministic methods.
     * @return a suitably constructed whitelisting classloader
     * @throws URISyntaxException
     */
    public static WhitelistClassLoader of(final String auxiliaryClassPath, final boolean stripNonDeterministic) throws URISyntaxException {
        final WhitelistClassLoader out = new WhitelistClassLoader(stripNonDeterministic);
        out.candidacyStatus.setContextLoader(out);
        out.setupClasspath(auxiliaryClassPath);
        return out;
    }

    public static WhitelistClassLoader of(final String auxiliaryClassPath) throws URISyntaxException {
        return of(auxiliaryClassPath, false);
    }

    public static WhitelistClassLoader of(final Path auxiliaryJar, final boolean stripNonDeterministic) {
        final WhitelistClassLoader out = new WhitelistClassLoader(stripNonDeterministic);
        out.candidacyStatus.setContextLoader(out);
        out.fileSystemSearchPath.add(auxiliaryJar);
        return out;
    }

    public static WhitelistClassLoader of(final Path auxiliaryJar) throws URISyntaxException {
        return of(auxiliaryJar, false);
    }

    /**
     * Static factory method. Used for recursive classloading
     *
     * @param other
     * @return a suitably constructed whitelisting classloader based on the state
     * of the passed classloader
     */
    public static WhitelistClassLoader of(final WhitelistClassLoader other) {
        final WhitelistClassLoader out = new WhitelistClassLoader(other);
//        out.candidacyStatus.setContextLoader(out);
        return out;
    }

    /**
     * Helper method that adds a jar to the path to be searched
     *
     * @param knownGoodJar
     */
    void addJarToSandbox(final Path knownGoodJar) {
        fileSystemSearchPath.add(knownGoodJar);
    }

    /**
     * Setup the auxiliary classpath so that classes that are not on the original
     * classpath can be scanned for.
     * Note that this this method hardcodes Unix conventions, so won't work on e.g. Windows
     *
     * @param auxiliaryClassPath
     * @throws URISyntaxException
     */
    void setupClasspath(final String auxiliaryClassPath) throws URISyntaxException {
        for (String entry : auxiliaryClassPath.split(":")) {
            if (entry.startsWith("/")) {
                fileSystemSearchPath.add(Paths.get(entry));
            } else {
                final URL u = getClass().getClassLoader().getResource(entry);
                primaryClasspathSearchPath.add(Paths.get(u.toURI()));
            }
        }
    }

    /**
     * @param qualifiedClassName
     * @return a class object that has been whitelist checked and is known to be
     * deterministic
     * @throws ClassNotFoundException
     */
    @Override
    public Class<?> findClass(final String qualifiedClassName) throws ClassNotFoundException {
        // One problem is that the requested class may refer to untransformed (but
        // deterministic) classes that will resolve & be loadable by the WLCL, but
        // in doing so, the name of the referenced class is rewritten and the name
        // by which it is now known does not have a mapping to a loaded class.
        // To solve this, we use the loadedClasses cache - on both possible keys
        // for the class (either of which will point to a transformed class object)
        Class<?> cls = loadedClasses.get(qualifiedClassName);
        if (cls != null) {
            return cls;
        }
        final String sandboxed = Utils.sandboxQualifiedTypeName(qualifiedClassName);
        cls = loadedClasses.get(sandboxed);
        if (cls != null) {
            return cls;
        }
        // Cache miss - so now try the superclass implementation
        try {
            cls = super.findClass(qualifiedClassName);
        } catch (ClassNotFoundException ignored) {
            // We actually need to load this ourselves, so find the path
            // corresponding to the directory where the classfile lives.
            // Note that for jar files this might be a "virtual" Path object
            classInternalName = Utils.convertQualifiedClassNameToInternalForm(qualifiedClassName);
            classDir = locateClassfileDir(classInternalName);
            try {
                final boolean isDeterministic = scan();
                if (isDeterministic || removeNonDeterministicMethods) {
                    final Path fullPathToClass = classDir.resolve(classInternalName + ".class");
                    Set<String> methodsToRemove = new HashSet<>();
                    if (removeNonDeterministicMethods && !isDeterministic) {
                        methodsToRemove = candidacyStatus.getDisallowedMethods();
                    }

                    final byte[] classContents = Files.readAllBytes(fullPathToClass);
                    final byte[] instrumentedBytes = instrumentWithCosts(classContents, methodsToRemove);
                    if (!removeNonDeterministicMethods) {
                        // If we're in stripping mode, then trying to define the class
                        // will cause a transitive loading failure
                        cls = defineClass(null, instrumentedBytes, 0, instrumentedBytes.length);
                    }
                    transformedClasses.put(sandboxed, instrumentedBytes);
                } else {
                    throw new ClassNotFoundException("Class " + qualifiedClassName + " could not be loaded.", reason());
                }
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Saving class " + cls + " as " + qualifiedClassName);

        loadedClasses.put(qualifiedClassName, cls);

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Saving class " + cls + " as " + sandboxed);

        loadedClasses.put(sandboxed, cls);

        return cls;
    }

    /**
     * Using the ASM library read in the currentClass's byte code and visit the call
     * sites within it.  Whilst visiting, check to see if the classes/methods visited
     * are deterministic and therefore safe to load.
     *
     * @return true if the current class is safe to be loaded
     * @throws java.io.IOException
     */
    public boolean scan() throws IOException {
        try (final InputStream in = Files.newInputStream(classDir.resolve(classInternalName + ".class"))) {
            try {
                final ClassReader classReader = new ClassReader(in);

                // Useful for debug, you can pass in the traceClassVisitor as an extra parameter if needed
                // PrintWriter printWriter = new PrintWriter(System.out);
                // TraceClassVisitor traceClassVisitor = new TraceClassVisitor(printWriter);
                final ClassVisitor whitelistCheckingClassVisitor
                        = new WhitelistCheckingClassVisitor(classInternalName, candidacyStatus);

                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("About to read class: " + classInternalName);

                // If there's debug info in the class, don't look at that whilst visiting
                classReader.accept(whitelistCheckingClassVisitor, ClassReader.SKIP_DEBUG);
            } catch (Exception ex) {
                LOGGER.error("Exception whilst reading class: " + classInternalName, ex);
            }
        }

        return candidacyStatus.isLoadable();
    }

    /**
     * Helper method that takes a class name (in internal format) and returns a Path
     * corresponding to the dir where the classfile was found. We are essentially working
     * around a limitation of the ASM library that does not integrate cleanly with Java 7
     * NIO.2 Path APIs. This method also performs a couple of basic sanity check on the
     * class file (e.g. that it exists, is a regular file and is readable).
     *
     * @param internalClassName
     * @return a path object that corresponds to a class that has been found
     * @throws ClassNotFoundException
     */
    Path locateClassfileDir(final String internalClassName) throws ClassNotFoundException {
        // Check the primaryClasspathSearchPath
        for (final Path p : primaryClasspathSearchPath) {
            final Path check = Paths.get(p.toString(), internalClassName + ".class");

            if (Files.isRegularFile(check)) {
                if (!Files.isReadable(check)) {
                    throw new IllegalArgumentException("File " + check + " found but is not readable");
                }
                return p;
            }
        }
        for (final Path p : fileSystemSearchPath) {
            final Path check = p.resolve(internalClassName + ".class");
            if (Files.isRegularFile(check)) {
                if (!Files.isReadable(check)) {
                    throw new IllegalArgumentException("File " + check + " found but is not readable");
                }
                return p;
            }
        }

        throw new ClassNotFoundException("Requested class "
                + Utils.convertInternalFormToQualifiedClassName(internalClassName) + " could not be found");
    }

    /**
     * Instruments a class with runtime cost accounting
     *
     * @param originalClassContents
     * @param methodsToRemove
     * @return the byte array that represents the transformed class
     */
    public byte[] instrumentWithCosts(final byte[] originalClassContents, final Set<String> methodsToRemove) {
        final ClassReader reader = new ClassReader(originalClassContents);
        final ClassWriter writer = new SandboxAwareClassWriter(this, reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final ClassVisitor remapper = new ClassRemapper(writer, new SandboxRemapper());
        final ClassVisitor coster = new ClassVisitor(Opcodes.ASM5, remapper) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
                final MethodVisitor baseMethodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
                return new CostInstrumentingMethodVisitor(baseMethodVisitor, access, name, desc);
            }
        };
        reader.accept(coster, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /**
     * Creates a jar archive of all the transformed classes that this classloader
     * has loaded.
     *
     * @return true on success, false on failure
     * @throws java.io.IOException
     * @throws java.net.URISyntaxException
     */
    public void createJar() throws IOException, URISyntaxException {
        final Map<String, String> env = new HashMap<>();
        env.put("create", String.valueOf(!outputJarPath.toFile().exists()));

        final URI fileUri = outputJarPath.toUri();
        final URI zipUri = new URI("jar:" + fileUri.getScheme(), fileUri.getPath(), null);

        try (final FileSystem zfs = FileSystems.newFileSystem(zipUri, env)) {
            final Path jarRoot = zfs.getRootDirectories().iterator().next();

            for (final Map.Entry<String, byte[]> stringEntry : transformedClasses.entrySet()) {
                final byte[] newClassDef = stringEntry.getValue();
                final String relativePathName = Utils.convertQualifiedClassNameToInternalForm(stringEntry.getKey()) + ".class";
                final Path outPath = jarRoot.resolve(relativePathName);

                Files.createDirectories(outPath.getParent());
                Files.write(outPath, newClassDef);
            }
        }
    }

    /**
     * Getter method for the reason for failure
     *
     * @return
     */
    public WhitelistClassloadingException reason() {
        return candidacyStatus.getReason();
    }

    /**
     * Getter method for the method candidacy status
     *
     * @return
     */
    public CandidacyStatus getCandidacyStatus() {
        return candidacyStatus;
    }

    public Path getOutpurJarPath() {
        return outputJarPath;
    }

    public void setOutpurJarPath(Path outpurJarPath) {
        this.outputJarPath = outpurJarPath;
    }

    public Set<String> cachedClasses() {
        return loadedClasses.keySet();
    }
}
