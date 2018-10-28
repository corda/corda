package net.corda.djvm.execution;

import net.corda.djvm.TestBase;
import net.corda.djvm.WithJava;
import static net.corda.djvm.messages.Severity.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class SandboxEnumJavaTest extends TestBase {

    @Test
    public void testEnumInsideSandbox() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<Integer, String[]> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            ExecutionSummaryWithResult<String[]> output = WithJava.run(executor, TransformEnum.class, 0);
            assertThat(output.getResult())
                    .isEqualTo(new String[]{ "ONE", "TWO", "THREE" });
            return null;
        });
    }

    @Test
    public void testReturnEnumFromSandbox() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<String, ExampleEnum> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            ExecutionSummaryWithResult<ExampleEnum> output = WithJava.run(executor, FetchEnum.class, "THREE");
            assertThat(output.getResult())
                    .isEqualTo(ExampleEnum.THREE);
            return null;
        });
    }

    @Test
    public void testWeCanIdentifyClassAsEnum() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<ExampleEnum, Boolean> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            ExecutionSummaryWithResult<Boolean> output = WithJava.run(executor, AssertEnum.class, ExampleEnum.THREE);
            assertThat(output.getResult()).isTrue();
            return null;
        });
    }

    @Test
    public void testWeCanCreateEnumMap() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<ExampleEnum, Integer> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            ExecutionSummaryWithResult<Integer> output = WithJava.run(executor, UseEnumMap.class, ExampleEnum.TWO);
            assertThat(output.getResult()).isEqualTo(1);
            return null;
        });
    }

    @Test
    public void testWeCanCreateEnumSet() {
        parentedSandbox(WARNING, true, ctx -> {
            SandboxExecutor<ExampleEnum, Boolean> executor = new DeterministicSandboxExecutor<>(ctx.getConfiguration());
            ExecutionSummaryWithResult<Boolean> output = WithJava.run(executor, UseEnumSet.class, ExampleEnum.ONE);
            assertThat(output.getResult()).isTrue();
            return null;
        });
    }

    public static class AssertEnum implements Function<ExampleEnum, Boolean> {
        @Override
        public Boolean apply(ExampleEnum input) {
            return input.getClass().isEnum();
        }
    }

    public static class TransformEnum implements Function<Integer, String[]> {
        @Override
        public String[] apply(Integer input) {
            return Stream.of(ExampleEnum.values()).map(ExampleEnum::name).toArray(String[]::new);
        }
    }

    public static class FetchEnum implements Function<String, ExampleEnum> {
        public ExampleEnum apply(String input) {
            return ExampleEnum.valueOf(input);
        }
    }

    public static class UseEnumMap implements Function<ExampleEnum, Integer> {
        @Override
        public Integer apply(ExampleEnum input) {
            Map<ExampleEnum, String> map = new EnumMap<>(ExampleEnum.class);
            map.put(input, input.name());
            return map.size();
        }
    }

    public static class UseEnumSet implements Function<ExampleEnum, Boolean> {
        @Override
        public Boolean apply(ExampleEnum input) {
            return EnumSet.allOf(ExampleEnum.class).contains(input);
        }
    }
}
