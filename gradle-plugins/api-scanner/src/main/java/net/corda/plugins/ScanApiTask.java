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
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class ScanApiTask extends DefaultTask {
    private static final int CLASS_MASK = Modifier.classModifiers();
    private static final int INTERFACE_MASK = Modifier.interfaceModifiers() & ~Modifier.ABSTRACT;

    private final ConfigurableFileCollection sources;
    private final ConfigurableFileCollection classpath;
    private boolean verbose;

    public ScanApiTask() {
        sources = getProject().files();
        classpath = getProject().files();
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

    public boolean getVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @TaskAction
    public void scan() {
        File outputDir = new File(getProject().getBuildDir(), "api");
        if (outputDir.isDirectory() || outputDir.mkdirs()) {
            try (Scanner scanner = new Scanner(outputDir, classpath)) {
                for (File source : sources) {
                    scanner.scan(source);
                }
            } catch (IOException e) {
                getLogger().error("", e);
            }
        } else {
            getLogger().error("Cannot create directory '{}'", outputDir.getAbsolutePath());
        }
    }

    private class Scanner implements Closeable {
        private final URLClassLoader classpathLoader;
        private final File outputDir;

        Scanner(File outputDir, URLClassLoader classpathLoader) {
            this.classpathLoader = classpathLoader;
            this.outputDir = outputDir;
        }

        Scanner(File outputDir, FileCollection classpath) throws MalformedURLException {
            this(outputDir, new URLClassLoader(toURLs(classpath)));
        }

        @Override
        public void close() throws IOException {
            classpathLoader.close();
        }

        void scan(File source) {
            File output = new File(outputDir, source.getName().replaceAll(".jar$", ".txt"));
            try (
                URLClassLoader appLoader = new URLClassLoader(new URL[]{ toURL(source) }, classpathLoader);
                PrintWriter writer = new PrintWriter(output, "UTF-8")
            ) {
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
            } catch (IOException e) {
                getLogger().error("API scan has failed", e);
            }
        }

        private void writeApis(PrintWriter writer, ScanResult result) {
            Map<String, ClassInfo> allInfo = result.getClassNameToClassInfo();
            result.getNamesOfAllClasses().forEach(className -> {
                if (className.contains(".internal.")) {
                    return;
                }
                ClassInfo classInfo = allInfo.get(className);
                if (classInfo.getClassLoaders() == null) {
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
            methods.sort(new MethodComparator());
            for (MethodInfo method : methods) {
                if (method.isPublic() || method.isProtected()) {
                    writer.append("  ").println(method);
                }
            }
        }

        private void writeFields(PrintWriter output, List<FieldInfo> fields) {
            fields.sort(new FieldComparator());
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
