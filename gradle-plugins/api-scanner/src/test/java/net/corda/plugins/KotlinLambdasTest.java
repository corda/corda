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

public class KotlinLambdasTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        copyResourceTo("kotlin-lambdas/build.gradle", buildFile);
    }

    @Test
    public void testKotlinLambdas() throws IOException {
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

        assertThat(output).contains("net.corda.example.LambdaExpressions$testing$$inlined$schedule$1");

        Path api = pathOf(testProjectDir, "build", "api", "kotlin-lambdas.txt");
        assertThat(api).isRegularFile();
        assertEquals("public final class net.corda.example.LambdaExpressions extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public final void testing(kotlin.Unit)\n" +
            "##\n", CopyUtils.toString(api));
    }
}
