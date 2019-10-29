package net.corda.testing;

import org.gradle.api.DefaultTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParallelTestGroup extends DefaultTask {

    DistributeTestsBy distribution = DistributeTestsBy.METHOD;

    List<String> groups = new ArrayList<>();
    int shardCount = 20;
    int coresToUse = 4;
    int gbOfMemory = 4;
    boolean printToStdOut = true;
    PodLogLevel logLevel = PodLogLevel.INFO;

    public void numberOfShards(int shards) {
        this.shardCount = shards;
    }

    public void podLogLevel(PodLogLevel level) {
        this.logLevel = level;
    }

    public void distribute(DistributeTestsBy dist) {
        this.distribution = dist;
    }

    public void coresPerFork(int cores) {
        this.coresToUse = cores;
    }

    public void memoryInGbPerFork(int gb) {
        this.gbOfMemory = gb;
    }

    //when this is false, only containers will "failed" exit codes will be printed to stdout
    public void streamOutput(boolean print) {
        this.printToStdOut = print;
    }

    public void testGroups(String... group) {
        testGroups(Arrays.asList(group));
    }

    public void testGroups(List<String> group) {
        groups.addAll(group);
    }

}
