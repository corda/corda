package net.corda.djvm;

import net.corda.djvm.execution.ExecutionSummaryWithResult;
import net.corda.djvm.execution.SandboxException;
import net.corda.djvm.execution.SandboxExecutor;
import net.corda.djvm.source.ClassSource;

import java.util.function.Function;

public interface WithJava {

    static <T,R> ExecutionSummaryWithResult<R> run(
            SandboxExecutor<T, R> executor, Class<? extends Function<T,R>> task, T input) {
        try {
            return executor.run(ClassSource.fromClassName(task.getName(), null), input);
        } catch (Exception e) {
            if (e instanceof SandboxException) {
                throw asRuntime(e.getCause());
            } else {
                throw asRuntime(e);
            }
        }
    }

    static RuntimeException asRuntime(Throwable t) {
        return (t instanceof RuntimeException) ? (RuntimeException) t : new RuntimeException(t.getMessage(), t);
    }
}
