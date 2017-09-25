package net.corda.plugins;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;

public class ScanApiTask extends DefaultTask {
    private final ConfigurableFileCollection sources;

    public ScanApiTask() {
        sources = getProject().files();
    }

    @InputFiles
    public FileCollection getSources() {
        return sources;
    }

    public void setSources(FileCollection sources) {
        this.sources.setFrom(sources);
    }
}
