package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.junit.Assert.*;

public class AnnotatedFieldTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        copyResourceTo("annotated-field/build.gradle", buildFile);
    }

    @Test
    public void testAnnotatedField() throws IOException {
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

        Path api = pathOf(testProjectDir, "build", "api", "annotated-field.txt");
        assertThat(api).isRegularFile();
        assertThat(Files.readAllLines(api)).containsOnlyOnce(
            "public class net.corda.example.HasAnnotatedField extends java.lang.Object",
            "  @net.corda.example.A @net.corda.example.B @net.corda.example.C public static final String ANNOTATED_FIELD = \"<string-value>\""
        );
    }
}
