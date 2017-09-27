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
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import static java.util.stream.Collectors.toList;

@SuppressWarnings("unused")
public class ScanApi extends DefaultTask {
    private static final int CLASS_MASK = Modifier.classModifiers();
    private static final int INTERFACE_MASK = Modifier.interfaceModifiers() & ~Modifier.ABSTRACT;

    private final ConfigurableFileCollection sources;
    private final ConfigurableFileCollection classpath;
    private final File outputDir;
    private boolean verbose;

    public ScanApi() {
        sources = getProject().files();
        classpath = getProject().files();
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

        Scanner(URLClassLoader classpathLoader) {
            this.classpathLoader = classpathLoader;
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
            ScanResult result = new FastClasspathScanner("!", "-dir:")
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

        private void writeApis(PrintWriter writer, ScanResult result) {
            Map<String, ClassInfo> allInfo = result.getClassNameToClassInfo();
            result.getNamesOfAllClasses().forEach(className -> {
                if (className.contains(".internal.")) {
                    return;
                }
                ClassInfo classInfo = allInfo.get(className);
                if (classInfo.getClassLoaders() == null) {
                     // Ignore classes that belong to one of our target ClassLoader's parents.
                     return;
                }

                writeClass(writer, classInfo, result.classNameToClassRef(className));
                writeMethods(writer, classInfo.getMethodInfo());
                writeFields(writer, classInfo.getFieldInfo());
                writer.println("--");
            });
        }

        private void writeClass(PrintWriter writer, ClassInfo classInfo, Class<?> javaClass) {
            if (classInfo.isAnnotation()) {
                writer.append(Modifier.toString(javaClass.getModifiers() & INTERFACE_MASK));
                writer.append(" @interface ").print(classInfo);
            } else if (classInfo.isStandardClass()) {
                writer.append(Modifier.toString(javaClass.getModifiers() & CLASS_MASK));
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
                writer.append(Modifier.toString(javaClass.getModifiers() & INTERFACE_MASK));
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
                if (method.isPublic() || method.isProtected()) {
                    writer.append("  ").println(method);
                }
            }
        }

        private void writeFields(PrintWriter output, List<FieldInfo> fields) {
            Collections.sort(fields);
            for (FieldInfo field : fields) {
                if (field.isPublic() || field.isProtected()) {
                    output.append("  ").println(field);
                }
            }
        }
    }

    private static String stringOf(Collection<ClassInfo> items) {
        return items.stream().map(ClassInfo::toString).collect(Collectors.joining(", "));
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
