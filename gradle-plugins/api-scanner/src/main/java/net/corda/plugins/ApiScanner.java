package net.corda.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.jvm.tasks.Jar;

public class ApiScanner implements Plugin<Project> {

    /**
     * Identify the Gradle Jar tasks creating jars
     * without Maven classifiers, and generate API
     * documentation for them.
     * @param p Current project.
     */
    @Override
    public void apply(Project p) {
        p.getLogger().info("Applying API scanner to {}", p.getName());

        ScannerExtension extension = p.getExtensions().create("scanApi", ScannerExtension.class);

        p.afterEvaluate(project -> {
            TaskCollection<Jar> jarTasks = project.getTasks()
                .withType(Jar.class)
                .matching(jarTask -> jarTask.getClassifier().isEmpty() && jarTask.isEnabled());
            if (jarTasks.isEmpty()) {
                return;
            }

            project.getLogger().info("Adding scanApi task to {}", project.getName());
            project.getTasks().create("scanApi", ScanApi.class, scanTask -> {
                scanTask.setClasspath(compilationClasspath(project.getConfigurations()));
                scanTask.setSources(project.files(jarTasks));
                scanTask.setVerbose(extension.isVerbose());
                scanTask.setEnabled(extension.isEnabled());
                scanTask.dependsOn(jarTasks);
            });
        });
    }

    private static FileCollection compilationClasspath(ConfigurationContainer configurations) {
        return configurations.getByName("compile")
                .plus(configurations.getByName("compileOnly"));
    }
}
