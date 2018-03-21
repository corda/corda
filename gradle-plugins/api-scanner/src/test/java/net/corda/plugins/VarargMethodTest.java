package net.corda.plugins;

import org.gradle.testkit.runner.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.Assert.*;

public class VarargMethodTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        copyResourceTo("vararg-method/build.gradle", buildFile);
    }

    @Test
    public void testVarargMethod() throws IOException {
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

        Path api = pathOf(testProjectDir, "build", "api", "vararg-method.txt");
        assertThat(api).isRegularFile();
        assertEquals("public interface net.corda.example.VarargMethod\n" +
            "  public abstract void action(Object...)\n" +
            "##\n", CopyUtils.toString(api));
    }
}