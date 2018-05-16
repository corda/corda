package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.gradle.testkit.runner.TaskOutcome.*;
import static org.junit.Assert.*;

/**
 * JUnit rule to execute the scanApi Gradle task. This rule should be chained with TemporaryFolder.
 */
public class GradleProject implements TestRule {
    private static final String testGradleUserHome = System.getProperty("test.gradle.user.home", "");

    private final TemporaryFolder projectDir;
    private final String name;

    private String output;
    private Path api;

    public GradleProject(TemporaryFolder projectDir, String name) {
        this.projectDir = projectDir;
        this.name = name;
        this.output = "";
    }

    public Path getApi() {
        return api;
    }

    public List<String> getApiLines() throws IOException {
        // Files.readAllLines() uses UTF-8 by default.
        return (api == null) ? emptyList() : Files.readAllLines(api);
    }

    public String getApiText() throws IOException {
        return getApiLines().stream().collect(joining("\n"));
    }

    public String getOutput() {
        return output;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                installResource(projectDir, name + "/build.gradle");
                installResource(projectDir, "gradle.properties");

                BuildResult result = GradleRunner.create()
                    .withProjectDir(projectDir.getRoot())
                    .withArguments(getGradleArgsForTasks("scanApi"))
                    .withPluginClasspath()
                    .build();
                output = result.getOutput();
                System.out.println(output);

                BuildTask scanApi = result.task(":scanApi");
                assertNotNull(scanApi);
                assertEquals(SUCCESS, scanApi.getOutcome());

                api = pathOf(projectDir, "build", "api", name + ".txt");
                assertThat(api).isRegularFile();
                base.evaluate();
            }
        };
    }

    public static Path pathOf(TemporaryFolder folder, String... elements) {
        return Paths.get(folder.getRoot().getAbsolutePath(), elements);
    }

    public static List<String> getGradleArgsForTasks(String... taskNames) {
        List<String> args = new ArrayList<>(taskNames.length + 3);
        Collections.addAll(args, taskNames);
        args.add("--info");
        if (!testGradleUserHome.isEmpty()) {
            Collections.addAll(args,"-g", testGradleUserHome);
        }
        return args;
    }
}
