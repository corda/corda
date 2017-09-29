package net.corda.plugins;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.scanner.ClassInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.FieldInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.MethodInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.StreamSupport;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.*;

@SuppressWarnings("unused")
public class ScanApi extends DefaultTask {
    private static final int CLASS_MASK = Modifier.classModifiers();
    private static final int INTERFACE_MASK = Modifier.interfaceModifiers() & ~Modifier.ABSTRACT;
    private static final int METHOD_MASK = Modifier.methodModifiers();
    private static final int FIELD_MASK = Modifier.fieldModifiers();
    private static final int VISIBILITY_MASK = Modifier.PUBLIC | Modifier.PROTECTED;

    /**
     * This information has been lifted from:
     * @link <a href="https://github.com/JetBrains/kotlin/blob/master/core/runtime.jvm/src/kotlin/Metadata.kt">Metadata.kt</a>
     */
    private static final String KOTLIN_METADATA = "kotlin.Metadata";
    private static final String KOTLIN_CLASSTYPE_METHOD = "k";
    private static final int KOTLIN_SYNTHETIC = 3;

    private final ConfigurableFileCollection sources;
    private final ConfigurableFileCollection classpath;
    private final Set<String> excludeClasses;
    private final File outputDir;
    private boolean verbose;

    public ScanApi() {
        sources = getProject().files();
        classpath = getProject().files();
        excludeClasses = new LinkedHashSet<>();
        outputDir = new File(getProject().getBuildDir(), "api");
    }

    @Input
    public FileCollection getSources() {
        return sources;
    }

    void setSources(FileCollection sources) {
        this.sources.setFrom(sources);
    }

    @Input
    public FileCollection getClasspath() {
        return classpath;
    }

    void setClasspath(FileCollection classpath) {
        this.classpath.setFrom(classpath);
    }

    @Input
    public Collection<String> getExcludeClasses() {
        return unmodifiableSet(excludeClasses);
    }

    void setExcludeClasses(Collection<String> excludeClasses) {
        this.excludeClasses.clear();
        this.excludeClasses.addAll(excludeClasses);
    }

    @OutputFiles
    public FileCollection getTargets() {
        return getProject().files(
            StreamSupport.stream(sources.spliterator(), false)
                .map(this::toTarget)
                .collect(toList())
        );
    }

    public boolean isVerbose() {
        return verbose;
    }

    void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private File toTarget(File source) {
        return new File(outputDir, source.getName().replaceAll(".jar$", ".txt"));
    }

    @TaskAction
    public void scan() {
        if (outputDir.isDirectory() || outputDir.mkdirs()) {
            try (Scanner scanner = new Scanner(classpath)) {
                for (File source : sources) {
                    scanner.scan(source);
                }
            } catch (IOException e) {
                getLogger().error("Failed to write API file", e);
            }
        } else {
            getLogger().error("Cannot create directory '{}'", outputDir.getAbsolutePath());
        }
    }

    class Scanner implements Closeable {
        private final URLClassLoader classpathLoader;
        private final Class<? extends Annotation> metadataClass;
        private final Method classTypeMethod;

        @SuppressWarnings("unchecked")
        Scanner(URLClassLoader classpathLoader) {
            this.classpathLoader = classpathLoader;

            Class<? extends Annotation> kClass;
            Method kMethod;
            try {
                kClass = (Class<Annotation>) Class.forName(KOTLIN_METADATA, true, classpathLoader);
                kMethod = kClass.getDeclaredMethod(KOTLIN_CLASSTYPE_METHOD);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                kClass = null;
                kMethod = null;
            }

            metadataClass = kClass;
            classTypeMethod = kMethod;
        }

        Scanner(FileCollection classpath) throws MalformedURLException {
            this(new URLClassLoader(toURLs(classpath)));
        }

        @Override
        public void close() throws IOException {
            classpathLoader.close();
        }

        void scan(File source) {
            File target = toTarget(source);
            try (
                URLClassLoader appLoader = new URLClassLoader(new URL[]{ toURL(source) }, classpathLoader);
                PrintWriter writer = new PrintWriter(target, "UTF-8")
            ) {
                scan(writer, appLoader);
            } catch (IOException e) {
                getLogger().error("API scan has failed", e);
            }
        }

        void scan(PrintWriter writer, ClassLoader appLoader) {
            ScanResult result = new FastClasspathScanner(getScanSpecification())
                .overrideClassLoaders(appLoader)
                .ignoreParentClassLoaders()
                .ignoreMethodVisibility()
                .ignoreFieldVisibility()
                .enableMethodInfo()
                .enableFieldInfo()
                .verbose(verbose)
                .scan();
            writeApis(writer, result);
        }

        private String[] getScanSpecification() {
            String[] spec = new String[2 + excludeClasses.size()];
            spec[0] = "!";     // Don't blacklist system classes from the output.
            spec[1] = "-dir:"; // Ignore classes on the filesystem.

            int i = 2;
            for (String excludeClass : excludeClasses) {
                spec[i++] = '-' + excludeClass;
            }
            return spec;
        }

        private void writeApis(PrintWriter writer, ScanResult result) {
            Map<String, ClassInfo> allInfo = result.getClassNameToClassInfo();
            result.getNamesOfAllClasses().forEach(className -> {
                if (className.contains(".internal.")) {
                    // These classes belong to internal Corda packages.
                    return;
                }
                ClassInfo classInfo = allInfo.get(className);
                if (classInfo.getClassLoaders() == null) {
                    // Ignore classes that belong to one of our target ClassLoader's parents.
                    return;
                }

                Class<?> javaClass = result.classNameToClassRef(className);
                if (!isVisible(javaClass.getModifiers())) {
                    // Excludes private and package-protected classes
                    return;
                }

                int kotlinClassType = getKotlinClassType(javaClass);
                if (kotlinClassType == KOTLIN_SYNTHETIC) {
                    // Exclude classes synthesised by the Kotlin compiler.
                    return;
                }

                writeClass(writer, classInfo, javaClass.getModifiers());
                writeMethods(writer, classInfo.getMethodAndConstructorInfo());
                writeFields(writer, classInfo.getFieldInfo());
                writer.println("##");
            });
        }

        private void writeClass(PrintWriter writer, ClassInfo classInfo, int modifiers) {
            if (classInfo.isAnnotation()) {
                writer.append(Modifier.toString(modifiers & INTERFACE_MASK));
                writer.append(" @interface ").print(classInfo);
            } else if (classInfo.isStandardClass()) {
                writer.append(Modifier.toString(modifiers & CLASS_MASK));
                writer.append(" class ").print(classInfo);
                Set<ClassInfo> superclasses = classInfo.getDirectSuperclasses();
                if (!superclasses.isEmpty()) {
                    writer.append(" extends ").print(stringOf(superclasses));
                }
                Set<ClassInfo> interfaces = classInfo.getDirectlyImplementedInterfaces();
                if (!interfaces.isEmpty()) {
                    writer.append(" implements ").print(stringOf(interfaces));
                }
            } else {
                writer.append(Modifier.toString(modifiers & INTERFACE_MASK));
                writer.append(" interface ").print(classInfo);
                Set<ClassInfo> superinterfaces = classInfo.getDirectSuperinterfaces();
                if (!superinterfaces.isEmpty()) {
                    writer.append(" extends ").print(stringOf(superinterfaces));
                }
            }
            writer.println();
        }

        private void writeMethods(PrintWriter writer, List<MethodInfo> methods) {
            Collections.sort(methods);
            for (MethodInfo method : methods) {
                if (isVisible(method.getAccessFlags()) // Only public and protected methods
                        && isValid(method.getAccessFlags(), METHOD_MASK) // Excludes bridge and synthetic methods
                        && !isKotlinInternalScope(method)) {
                    writer.append("  ").println(method);
                }
            }
        }

        private void writeFields(PrintWriter output, List<FieldInfo> fields) {
            Collections.sort(fields);
            for (FieldInfo field : fields) {
                if (isVisible(field.getAccessFlags()) && isValid(field.getAccessFlags(), FIELD_MASK)) {
                    output.append("  ").println(field);
                }
            }
        }

        private int getKotlinClassType(Class<?> javaClass) {
            if (metadataClass != null) {
                Annotation metadata = javaClass.getAnnotation(metadataClass);
                if (metadata != null) {
                    try {
                        return (int) classTypeMethod.invoke(metadata);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        getLogger().error("Failed to read Kotlin annotation", e);
                    }
                }
            }
            return 0;
        }
    }

    private static boolean isKotlinInternalScope(MethodInfo method) {
        return method.getMethodName().indexOf('$') >= 0;
    }

    private static boolean isValid(int modifiers, int mask) {
        return (modifiers & mask) == modifiers;
    }

    private static boolean isVisible(int accessFlags) {
        return (accessFlags & VISIBILITY_MASK) != 0;
    }

    private static String stringOf(Collection<ClassInfo> items) {
        return items.stream().map(ClassInfo::toString).collect(joining(", "));
    }

    private static URL toURL(File file) throws MalformedURLException {
        return file.toURI().toURL();
    }

    private static URL[] toURLs(Iterable<File> files) throws MalformedURLException {
        List<URL> urls = new LinkedList<>();
        for (File file : files) {
            urls.add(toURL(file));
        }
        return urls.toArray(new URL[urls.size()]);
    }
}
