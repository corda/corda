package net.corda.common.javaxpersistence;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Converts a class that was built against Java Persistence ({@code javax.persistence}, i.e. a CorDapp built for Corda 4.14 or earlier)
 * to Jakarta Persistence ({@code jakarta.persistence}), which is what Hibernate 7 reads, by renaming the package in the bytes of the
 * class as it is loaded. This is what the application servers did for Java EE applications when they moved to Jakarta EE, and it lets
 * such a CorDapp run on a Corda 4.15 node without being rebuilt: the rename covers the annotations of the schema classes (nested
 * annotations and enum values included), {@code AttributeConverter} implementations, code that uses the {@code EntityManager},
 * {@code Query} and criteria types in {@code ServiceHub.withEntityManager} blocks (lambdas included) and {@code catch} clauses of the
 * JPA exception types.
 * <p>
 * The node's CorDapp class loader runs it over the classes of the CorDapp jars, and the javax persistence test agent runs it, as a
 * {@code ClassFileTransformer}, over the classes of a test JVM, where the CorDapp's classes are on the test classpath. Both decide with
 * {@link #needsConversion(String, byte[])}: only a class that refers to {@code javax.persistence} and not to {@code jakarta.persistence}
 * is converted (a class that carries both sets of annotations, i.e. a CorDapp built to run on Corda 4.14 and 4.15 alike, must be left
 * alone, as the rename would duplicate its annotations), and never a class of the {@code javax.persistence} API itself, which a CorDapp
 * may bundle (renaming it would give it a name other than the one it is loaded under).
 * <p>
 * What cannot be renamed is Hibernate's own {@code @Type(type = "...")} annotation, whose members changed in Hibernate 6: the type names
 * Corda documents ({@code uuid-char}, {@code corda-wrapper-binary}) are replaced by the {@code @JdbcTypeCode} Corda's own schemas use
 * on Hibernate 7, any other one, and {@code @TypeDef}, make the conversion fail with a {@link JavaxPersistenceConversionError} naming
 * the class and the member (such a CorDapp has to be rebuilt).
 * <p>
 * This class has no dependency other than ASM, so that the test agent can bundle it and run before anything else is loaded.
 */
public final class JavaxPersistenceTransformer {
    /** The package that is renamed, in the internal form used in class files. */
    public static final String JAVAX_PERSISTENCE_PACKAGE = "javax/persistence/";
    /** The package it is renamed to, in the internal form used in class files. */
    public static final String JAKARTA_PERSISTENCE_PACKAGE = "jakarta/persistence/";

    /**
     * System property the javax persistence test agent sets to {@code true} once it is installed on the JVM, so that the node can
     * tell, when it finds an unconverted class, whether the agent is there at all.
     */
    public static final String AGENT_INSTALLED_PROPERTY = "net.corda.javaxpersistence.agent";

    /**
     * Prefix of the system properties (one per class, suffixed with the binary name of the class) in which the javax persistence test
     * agent records the message of a conversion that failed. The JVM ignores exceptions thrown by a class file transformer and loads
     * the class unconverted, so this is how the node learns why it finds an unconverted class.
     */
    public static final String AGENT_FAILURE_PROPERTY_PREFIX = "net.corda.javaxpersistence.agent.failure.";

    /** The Hibernate 5 type names Corda documents a Hibernate 7 mapping for, with the JDBC type code of that mapping. */
    public static final Map<String, Integer> SUBSTITUTED_HIBERNATE_5_TYPES;

    static {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("uuid-char", Types.VARCHAR);
        types.put("corda-wrapper-binary", Types.VARBINARY);
        SUBSTITUTED_HIBERNATE_5_TYPES = Collections.unmodifiableMap(types);
    }

    private static final byte[] JAVAX_PERSISTENCE_BYTES = JAVAX_PERSISTENCE_PACKAGE.getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] JAKARTA_PERSISTENCE_BYTES = JAKARTA_PERSISTENCE_PACKAGE.getBytes(StandardCharsets.ISO_8859_1);
    private static final String HIBERNATE_TYPE = "Lorg/hibernate/annotations/Type;";
    private static final String HIBERNATE_TYPE_DEF = "Lorg/hibernate/annotations/TypeDef;";
    private static final String HIBERNATE_TYPE_DEFS = "Lorg/hibernate/annotations/TypeDefs;";
    private static final String HIBERNATE_JDBC_TYPE_CODE = "Lorg/hibernate/annotations/JdbcTypeCode;";
    private static final String REBUILD_ADVICE = "The CorDapp that contains the class needs to be rebuilt for Corda 4.15 - see the " +
            "\"Upgrading to Hibernate 7\" documentation.";

    private JavaxPersistenceTransformer() {
    }

    /**
     * Whether the bytes of a class file refer to a {@code javax.persistence} class without also referring to a {@code jakarta.persistence}
     * one (constant pool entries are ASCII-compatible). A class that refers to both is annotated for both persistence APIs on purpose.
     */
    public static boolean isBuiltForJavaxPersistenceOnly(byte[] classBytes) {
        return contains(classBytes, JAVAX_PERSISTENCE_BYTES) && !contains(classBytes, JAKARTA_PERSISTENCE_BYTES);
    }

    /**
     * Whether a class, given by its binary name ({@code javax.persistence.Entity}) or internal name ({@code javax/persistence/Entity}),
     * belongs to the {@code javax.persistence} API itself. Such a class refers to its own package, so it looks built for
     * {@code javax.persistence}, but renaming it would give it a name other than the one it is loaded under: a CorDapp that bundles the
     * API jar keeps those classes, which nothing converted refers to any more.
     */
    public static boolean isPersistenceApiClass(String className) {
        return className.startsWith(JAVAX_PERSISTENCE_PACKAGE) || className.startsWith("javax.persistence.");
    }

    /**
     * Whether the class with the given name (binary or internal form) and class file bytes is to be converted: it is built for
     * {@code javax.persistence} only and is not part of the {@code javax.persistence} API itself.
     */
    public static boolean needsConversion(String className, byte[] classBytes) {
        return !isPersistenceApiClass(className) && isBuiltForJavaxPersistenceOnly(classBytes);
    }

    /**
     * The bytes of the class with every reference to {@code javax.persistence} renamed to {@code jakarta.persistence} and the Hibernate 5
     * type annotations substituted. {@code className} is only used in error messages.
     *
     * @throws JavaxPersistenceConversionError if the class uses a Hibernate 5 annotation that has no substitute.
     */
    public static byte[] transform(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        // A fresh writer, so that the constant pool of the result does not keep the javax.persistence entries of the original.
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new HibernateTypeRewriter(className.replace('/', '.'), new ClassRemapper(writer, PackageRemapper.INSTANCE)), 0);
        return writer.toByteArray();
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

    private static final class PackageRemapper extends Remapper {
        static final PackageRemapper INSTANCE = new PackageRemapper();

        private PackageRemapper() {
            super(Opcodes.ASM9);
        }

        @Override
        public String map(String internalName) {
            if (internalName.startsWith(JAVAX_PERSISTENCE_PACKAGE)) {
                return JAKARTA_PERSISTENCE_PACKAGE + internalName.substring(JAVAX_PERSISTENCE_PACKAGE.length());
            }
            return internalName;
        }
    }

    /**
     * Replaces {@code @Type(type = "<name>")} on fields and methods by {@code @JdbcTypeCode(<code>)} for the type names in
     * {@link #SUBSTITUTED_HIBERNATE_5_TYPES}, and fails for any other Hibernate 5 type annotation.
     */
    private static final class HibernateTypeRewriter extends ClassVisitor {
        private final String className;

        HibernateTypeRewriter(String className, ClassVisitor next) {
            super(Opcodes.ASM9, next);
            this.className = className;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(HIBERNATE_TYPE_DEF) || descriptor.equals(HIBERNATE_TYPE_DEFS)) {
                throw new JavaxPersistenceConversionError("The class " + className + " uses the Hibernate 5 annotation @TypeDef, which " +
                        "no longer exists in Hibernate 7. " + REBUILD_ADVICE);
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            FieldVisitor fieldVisitor = super.visitField(access, name, descriptor, signature, value);
            if (fieldVisitor == null) return null;
            return new FieldVisitor(Opcodes.ASM9, fieldVisitor) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    if (annotationDescriptor.equals(HIBERNATE_TYPE)) return typeSubstitution(name, fieldVisitor::visitAnnotation);
                    return super.visitAnnotation(annotationDescriptor, visible);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (methodVisitor == null) return null;
            return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    if (annotationDescriptor.equals(HIBERNATE_TYPE)) return typeSubstitution(name, methodVisitor::visitAnnotation);
                    return super.visitAnnotation(annotationDescriptor, visible);
                }
            };
        }

        /** Consumes a {@code @Type} annotation and, once its {@code type} is known, emits the substitute through {@code emit} (descriptor, visible). */
        private AnnotationVisitor typeSubstitution(String memberName, BiFunction<String, Boolean, AnnotationVisitor> emit) {
            return new AnnotationVisitor(Opcodes.ASM9) {
                private String typeName;

                @Override
                public void visit(String name, Object value) {
                    if ("type".equals(name) && value instanceof String) typeName = (String) value;
                }

                @Override
                public void visitEnd() {
                    Integer jdbcTypeCode = SUBSTITUTED_HIBERNATE_5_TYPES.get(typeName);
                    if (jdbcTypeCode == null) {
                        throw new JavaxPersistenceConversionError("The class " + className + " uses the Hibernate 5 annotation " +
                                "@Type(type = \"" + typeName + "\") on " + memberName + ", which has no equivalent in Hibernate 7 that the " +
                                "node can substitute (it substitutes " + SUBSTITUTED_HIBERNATE_5_TYPES.keySet() + "). " + REBUILD_ADVICE);
                    }
                    AnnotationVisitor substitute = emit.apply(HIBERNATE_JDBC_TYPE_CODE, true);
                    if (substitute != null) {
                        substitute.visit("value", jdbcTypeCode);
                        substitute.visitEnd();
                    }
                }
            };
        }
    }
}
