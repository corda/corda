package net.corda.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.tasks.Jar;

public class ApiScanner implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().withType(Jar.class, (Jar jarTask) -> {
            if (jarTask.getClassifier().isEmpty()) {
                System.out.println("JAR: " + jarTask.getArchivePath().getAbsolutePath());
            }
        });
    }
}
