package net.corda.djvm.execution;

import net.corda.djvm.TestBase;
import net.corda.djvm.WithJava;
import static net.corda.djvm.messages.Severity.*;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SandboxThrowableJavaTest extends TestBase {

    @Test
    public void testUserExceptionHandling() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<String, String[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            ExecutionSummaryWithResult<String[]> output = WithJava.run(executor, ThrowAndCatchJavaExample.class, "Hello World!");
            assertThat(output.getResult())
                    .isEqualTo(new String[]{ "FIRST FINALLY", "BASE EXCEPTION", "Hello World!", "SECOND FINALLY" });
            return null;
        });
    }

    @Test
    public void testCheckedExceptions() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<String, String> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());

            ExecutionSummaryWithResult<String> success = WithJava.run(executor, JavaWithCheckedExceptions.class, "http://localhost:8080/hello/world");
            assertThat(success.getResult()).isEqualTo("/hello/world");

            ExecutionSummaryWithResult<String> failure = WithJava.run(executor, JavaWithCheckedExceptions.class, "nasty string");
            assertThat(failure.getResult()).isEqualTo("CATCH:Illegal character in path at index 5: nasty string");

            return null;
        });
    }

    public static class ThrowAndCatchJavaExample implements Function<String, String[]> {
        @Override
        public String[] apply(String input) {
            List<String> data = new LinkedList<>();
            try {
                try {
                    throw new MyExampleException(input);
                } finally {
                    data.add("FIRST FINALLY");
                }
            } catch (MyBaseException e) {
                data.add("BASE EXCEPTION");
                data.add(e.getMessage());
            } catch (Exception e) {
                data.add("NOT THIS ONE!");
            } finally {
                data.add("SECOND FINALLY");
            }

            return data.toArray(new String[0]);
        }
    }

    public static class JavaWithCheckedExceptions implements Function<String, String> {
        @Override
        public String apply(String input) {
            try {
                return new URI(input).getPath();
            } catch (URISyntaxException e) {
                return "CATCH:" + e.getMessage();
            }
        }
    }
}
