package net.corda.testing.javaxpersistence;

import net.corda.common.javaxpersistence.JavaxPersistenceConversionError;
import net.corda.common.javaxpersistence.JavaxPersistenceTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Java agent for the in-process tests (MockNetwork, MockServices, in-process driver nodes) of a CorDapp built against
 * {@code javax.persistence}, i.e. for Corda 4.14 or earlier. Such a test has the CorDapp's classes on the test classpath, from which the
 * JVM serves them before the node's CorDapp class loader, which converts them on a real node, is asked. The agent does the same
 * conversion one level lower: it runs the node's {@link JavaxPersistenceTransformer} over every class any class loader of the test JVM
 * defines, so that the classes carry the {@code jakarta.persistence} annotations Hibernate 7 reads, wherever they are loaded from.
 * Test code that itself uses {@code javax.persistence} types (a {@code withEntityManager} block in a test) is converted the same way.
 * <p>
 * Usage: {@code -javaagent:corda-javax-persistence-test-agent.jar} on the test JVM, before Quasar's agent. The transformer is registered
 * as retransformation-incapable, which the JVM runs before any retransformation-capable transformer such as Quasar's whatever the order
 * of the agents on the command line, so Quasar instruments converted classes, as it does on a node. {@code agentmain} lets the agent be
 * attached to a running JVM, but a class loaded before that moment stays unconverted, which the node then reports; starting the JVM with
 * the agent is the supported form.
 * <p>
 * The JVM ignores an exception thrown by a class file transformer and loads the class unchanged, so a class the transformer cannot
 * convert (a Hibernate 5 {@code @Type} with no substitute, or {@code @TypeDef}) is reported on the console and recorded in a system
 * property that the node's start-up check reads to name the class and the reason.
 */
public final class JavaxPersistenceTestAgent {
    /** Packages that are never converted: the JDK's, and this agent's own (which holds its relocated copies of the transformer and ASM). */
    private static final String[] SKIPPED_PACKAGE_PREFIXES = {
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "net/corda/testing/javaxpersistence/"
    };

    private JavaxPersistenceTestAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        install(instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        install(instrumentation);
    }

    private static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new Transformer(), false);
        System.setProperty(JavaxPersistenceTransformer.AGENT_INSTALLED_PROPERTY, "true");
    }

    static final class Transformer implements ClassFileTransformer {
        private static final ClassLoader PLATFORM_CLASS_LOADER = ClassLoader.getPlatformClassLoader();

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBytes) {
            // Classes of the JDK (bootstrap and platform class loaders) are skipped before their bytes are looked at.
            if (loader == null || loader == PLATFORM_CLASS_LOADER || className == null || isSkipped(className)) return null;
            if (!JavaxPersistenceTransformer.needsConversion(className, classBytes)) return null;
            String binaryName = className.replace('/', '.');
            try {
                return JavaxPersistenceTransformer.transform(classBytes, className);
            } catch (JavaxPersistenceConversionError e) {
                report(binaryName, e.getMessage());
            } catch (RuntimeException | LinkageError e) {
                report(binaryName, "the conversion failed with " + e);
            }
            return null;
        }

        private static boolean isSkipped(String className) {
            for (String prefix : SKIPPED_PACKAGE_PREFIXES) {
                if (className.startsWith(prefix)) return true;
            }
            return false;
        }

        private static void report(String binaryName, String reason) {
            System.setProperty(JavaxPersistenceTransformer.AGENT_FAILURE_PROPERTY_PREFIX + binaryName, reason);
            System.err.println("[corda-javax-persistence-test-agent] The class " + binaryName + " was left as built for javax.persistence, " +
                    "which Hibernate 7 (Corda 4.15) does not read: " + reason);
        }
    }
}
