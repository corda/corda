package net.corda.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.jvm.tasks.Jar;

public class ApiScanner implements Plugin<Project> {

    @Override
    public void apply(Project p) {
        p.getLogger().info("Applying API scanner to {}", p.getName());
        p.afterEvaluate(project -> {
            TaskCollection<Jar> jarTasks = project.getTasks()
                .withType(Jar.class)
                .matching(jarTask -> jarTask.getClassifier().isEmpty());
            if (jarTasks.isEmpty()) {
                return;
            }

            Jar jarTask = jarTasks.size() == 1 ? jarTasks.iterator().next() : jarTasks.getByName("jar");
            ScanApiTask scanTask = project.getTasks().create(
                                     "scanApi", ScanApiTask.class, task -> task.dependsOn(jarTask));
            scanTask.setSources(project.files(jarTask.getArchivePath()));
            scanTask.setClasspath(compilationClasspath(project.getConfigurations()));
        });
    }

    private static FileCollection compilationClasspath(ConfigurationContainer configurations) {
        return configurations.getByName("compile").plus(configurations.getByName("compileOnly"));
    }
}
