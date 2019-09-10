package net.corda.testing

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class ParallelTestGroup extends DefaultTask {

    List<String> groups = new ArrayList<>()

    void testGroups(String... group) {
        testGroups(group.toList())
    }

    void testGroups(List<String> group) {
        group.forEach {
            groups.add(it)
        }
    }

    @TaskAction
    def wire() {
    }


}
