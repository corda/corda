package net.corda.testing

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction

import java.util.concurrent.CompletableFuture

class RunInParallel extends DefaultTask {

    private List<Task> tasksToRunInParallel = new ArrayList<>()

    public RunInParallel runInParallel(Task... tasks) {
        for (Task task : tasks) {
            tasksToRunInParallel.add(task)
        }
        return this;
    }

    @TaskAction
    def void run() {
        tasksToRunInParallel.collect { t ->
            CompletableFuture.runAsync {
                def actions = t.getActions()
                for (Action action : actions) {
                    action.execute(t)
                }
            }
        }.join()
    }
}
