package net.corda.djvm;

import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxExecutor;
import net.corda.djvm.source.ClassSource;

import java.util.function.Function;

public interface WithJava {

    static <T,R> ExecutionSummaryWithResult<R> run(
            SandboxExecutor<T, R> executor, Class<? extends Function<T,R>> task, T input) {
        try {
            return executor.run(ClassSource.fromClassName(task.getName(), null), input);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

}
