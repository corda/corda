package net.corda.plugins;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import static java.util.stream.Collectors.toList;

@SuppressWarnings("unused")
public class GenerateApi extends DefaultTask {

    @TaskAction
    public void generate() {
        List<File> apiFiles = getProject().getAllprojects().stream()
            .flatMap(project -> project.getTasks()
                                    .withType(ScanApi.class)
                                    .matching(ScanApi::isEnabled)
                                    .stream())
            .flatMap(scanTask -> scanTask.getTargets().getFiles().stream())
            .sorted(Comparator.comparing(File::getName))
            .collect(toList());
    }
}
