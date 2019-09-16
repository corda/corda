package net.corda.testing

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class ParallelTestGroup extends DefaultTask {

    List<String> groups = new ArrayList<>()
    int shardCount = 20
    boolean printToStdOut = true

    void numberOfShards(int shards){
        this.shardCount = shards
    }

    //when this is false, only containers will "failed" exit codes will be printed to stdout
    void streamOutput(boolean print){
        this.printToStdOut = print
    }

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
