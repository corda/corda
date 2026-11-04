package net.corda.testing.javaxpersistence;

import net.corda.nodeapi.internal.persistence.JavaxPersistenceTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Java agent for the in-process tests (MockNetwork, MockServices, in-process driver nodes) of a CorDapp built against
 * {@code javax.persistence}, i.e. for Corda 4.14 or earlier. Such a test has the CorDapp's classes on the test classpath, from which the
 * JVM serves them before the node's CorDapp class loader, which converts them on a real node, is asked. The agent does the same
 * conversion one level lower: it runs the node's {@code JavaxPersistenceTransformer} over every class any class loader of the test JVM
 * defines that is built for {@code javax.persistence} only, so that the classes carry the {@code jakarta.persistence} annotations
 * Hibernate 7 reads, wherever they are loaded from. Test code that itself uses {@code javax.persistence} types (a {@code withEntityManager}
 * block in a test) is converted the same way.
 * <p>
 * The jar holds nothing but this class. The node's converter needs the node's Hibernate 7 to check the Hibernate references of a converted
 * class, so the agent takes it from where the test has both: the class loader that is loading the CorDapp's class (or, failing that, the
 * system class loader), looked up once per class loader. The conversion, what it substitutes and what it refuses are therefore exactly
 * the node's, and the {@code net.corda.cordapp.missingHibernateReferences} property is honoured in the test JVM too.
 * <p>
 * Usage: {@code -javaagent:corda-javax-persistence-test-agent.jar} on the test JVM, before Quasar's agent. The transformer is registered
 * as retransformation-incapable, which the JVM runs before any retransformation-capable transformer such as Quasar's whatever the order
 * of the agents on the command line, so Quasar instruments converted classes, as it does on a node. {@code agentmain} lets the agent be
 * attached to a running JVM, but a class loaded before that moment stays unconverted, which the node then reports; starting the JVM with
 * the agent is the supported form.
 * <p>
 * The JVM ignores an exception thrown by a class file transformer and loads the class unchanged, so a class the converter refuses is
 * reported on the console and recorded in a system property that the node's start-up check reads to name the class and the reason.
 */
public final class JavaxPersistenceTestAgent {
    private static final String TRANSFORMER_CLASS = "net.corda.nodeapi.internal.persistence.JavaxPersistenceTransformer";
    private static final String KOTLIN_FUNCTION1 = "kotlin.jvm.functions.Function1";
    private static final byte[] JAVAX_PERSISTENCE = "javax/persistence/".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] JAKARTA_PERSISTENCE = "jakarta/persistence/".getBytes(StandardCharsets.ISO_8859_1);

    /** Packages that are never converted: the JDK's, the persistence API itself (a CorDapp may bundle it), and this agent's own. */
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
        System.setProperty(JavaxPersistenceTransformer.TEST_AGENT_INSTALLED_PROPERTY, "true");
    }

    static final class Transformer implements ClassFileTransformer {
        private static final ClassLoader PLATFORM_CLASS_LOADER = ClassLoader.getPlatformClassLoader();
        /** The node's converter as seen from each class loader; null where it is not visible, which is reported once. */
        private final Map<ClassLoader, Converter> converters = new WeakHashMap<>();
        /** Set while a conversion runs on the thread: the classes the converter itself loads are not converted. */
        private final ThreadLocal<Boolean> converting = ThreadLocal.withInitial(() -> false);

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBytes) {
            // Classes of the JDK (bootstrap and platform class loaders) are skipped before their bytes are looked at.
            if (loader == null || loader == PLATFORM_CLASS_LOADER || className == null || isSkipped(className)) return null;
            if (!(contains(classBytes, JAVAX_PERSISTENCE) && !contains(classBytes, JAKARTA_PERSISTENCE))) return null;
            if (converting.get()) return null;
            String binaryName = className.replace('/', '.');
            converting.set(true);
            try {
                Converter converter = converterFor(loader, binaryName);
                return converter == null ? null : converter.convert(classBytes, binaryName, loader);
            } finally {
                converting.set(false);
            }
        }

        private Converter converterFor(ClassLoader loader, String binaryName) {
            synchronized (converters) {
                if (converters.containsKey(loader)) return converters.get(loader);
            }
            Converter converter = Converter.find(loader, binaryName);
            synchronized (converters) {
                converters.put(loader, converter);
            }
            return converter;
        }

        private static boolean isSkipped(String className) {
            for (String prefix : SKIPPED_PACKAGE_PREFIXES) {
                if (className.startsWith(prefix)) return true;
            }
            return false;
        }

        private static boolean contains(byte[] haystack, byte[] needle) {
            byte first = needle[0];
            int last = haystack.length - needle.length;
            outer:
            for (int i = 0; i <= last; i++) {
                if (haystack[i] != first) continue;
                for (int j = 1; j < needle.length; j++) {
                    if (haystack[i + j] != needle[j]) continue outer;
                }
                return true;
            }
            return false;
        }
    }

    /** The node's {@code JavaxPersistenceTransformer} as a class loader sees it, called reflectively so that the agent depends on nothing. */
    static final class Converter {
        private final Object transformer;
        private final Method transform;
        private final Constructor<?> options;
        private final Method refuseMissingReferencesByDefault;
        private final Object noBundledClass;

        private Converter(Class<?> transformerClass, ClassLoader loader) throws ReflectiveOperationException {
            transformer = transformerClass.getField("INSTANCE").get(null);
            Class<?> optionsClass = Class.forName(TRANSFORMER_CLASS + "$Options", true, loader);
            Class<?> function1 = Class.forName(KOTLIN_FUNCTION1, true, loader);
            transform = transformerClass.getMethod("transform", byte[].class, String.class, ClassLoader.class, optionsClass, function1);
            options = optionsClass.getConstructor(boolean.class, boolean.class);
            refuseMissingReferencesByDefault = transformerClass.getMethod("refuseMissingReferencesByDefault");
            // The node asks this whether a Hibernate class it does not have comes with the CorDapp; on a test classpath there is no such
            // distinction, and a Hibernate class that is not on the classpath is simply missing.
            noBundledClass = Proxy.newProxyInstance(loader, new Class<?>[]{function1}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "invoke": return Boolean.FALSE;
                    case "toString": return "bundledClass = false";
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
        }

        /**
         * The converter visible from {@code loader}, or from the system class loader, or null (reported once) where neither sees node-api.
         */
        static Converter find(ClassLoader loader, String binaryName) {
            for (ClassLoader candidate : new ClassLoader[]{loader, ClassLoader.getSystemClassLoader()}) {
                try {
                    return new Converter(Class.forName(TRANSFORMER_CLASS, true, candidate), candidate);
                } catch (ClassNotFoundException e) {
                    // Try the next candidate.
                } catch (ReflectiveOperationException | LinkageError e) {
                    report(binaryName, "the node's converter (" + TRANSFORMER_CLASS + ") could not be set up: " + e);
                    return null;
                }
            }
            report(binaryName, "the node's converter (" + TRANSFORMER_CLASS + ", in corda-node-api) is not on the class path of " + loader +
                    " or of the system class loader, so classes built for javax.persistence that they load stay unconverted");
            return null;
        }

        byte[] convert(byte[] classBytes, String binaryName, ClassLoader loader) {
            try {
                Object defaults = options.newInstance((boolean) refuseMissingReferencesByDefault.invoke(transformer), false);
                return (byte[]) transform.invoke(transformer, classBytes, binaryName, loader, defaults, noBundledClass);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof LinkageError && cause.getClass().getName().equals(TRANSFORMER_CLASS.replace("Transformer", "ConversionError"))) {
                    report(binaryName, cause.getMessage());
                } else {
                    report(binaryName, "the conversion failed with " + cause);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                report(binaryName, "the conversion failed with " + e);
            }
            return null;
        }

        private static void report(String binaryName, String reason) {
            System.setProperty(JavaxPersistenceTransformer.TEST_AGENT_FAILURE_PROPERTY_PREFIX + binaryName, reason);
            System.err.println("[corda-javax-persistence-test-agent] The class " + binaryName + " was left as built for javax.persistence, " +
                    "which Hibernate 7 (Corda 4.15) does not read: " + reason);
        }
    }
}
