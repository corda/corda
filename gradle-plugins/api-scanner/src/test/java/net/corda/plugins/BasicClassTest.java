package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import static org.gradle.testkit.runner.TaskOutcome.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class BasicClassTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        copyResourceTo("basic-class/build.gradle", buildFile);
    }

    @Test
    public void testBasicClass() throws IOException {
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments(getGradleArgsForTasks("scanApi"))
            .withPluginClasspath()
            .build();
        String output = result.getOutput();
        System.out.println(output);

        BuildTask scanApi = result.task(":scanApi");
        assertNotNull(scanApi);
        assertEquals(SUCCESS, scanApi.getOutcome());

        Path api = pathOf(testProjectDir, "build", "api", "basic-class.txt");
        assertThat(api).isRegularFile();
        assertEquals(
            "public class net.corda.example.BasicClass extends java.lang.Object\n" +
            "  public <init>(String)\n" +
            "  public String getName()\n" +
            "##\n", CopyUtils.toString(api));
    }
}
