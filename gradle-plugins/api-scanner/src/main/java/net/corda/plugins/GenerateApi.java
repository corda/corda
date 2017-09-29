package net.corda.plugins;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.nio.file.Files;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

@SuppressWarnings("unused")
public class GenerateApi extends DefaultTask {

    private final File outputDir;
    private String baseName;

    public GenerateApi() {
        outputDir = new File(getProject().getBuildDir(), "api");
        baseName = "api-" + getProject().getName();
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    @InputFiles
    public FileCollection getSources() {
        return getProject().files(getProject().getAllprojects().stream()
            .flatMap(project -> project.getTasks()
                         .withType(ScanApi.class)
                         .matching(ScanApi::isEnabled)
                         .stream())
            .flatMap(scanTask -> scanTask.getTargets().getFiles().stream())
            .sorted(comparing(File::getName))
            .collect(toList())
        );
    }

    @OutputFile
    public File getTarget() {
        return new File(outputDir, String.format("%s-%s.txt", baseName, getProject().getVersion()));
    }

    @TaskAction
    public void generate() {
        FileCollection apiFiles = getSources();
        if (!apiFiles.isEmpty() && (outputDir.isDirectory() || outputDir.mkdirs())) {
            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(getTarget()))) {
                for (File apiFile : apiFiles) {
                    Files.copy(apiFile.toPath(), output);
                }
            } catch (IOException e) {
                getLogger().error("Failed to generate API file", e);
            }
        }
    }
}
