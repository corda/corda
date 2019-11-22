package net.corda.testing;

import org.gradle.api.DefaultTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParallelTestGroup extends DefaultTask {

    private DistributeTestsBy distribution = DistributeTestsBy.METHOD;
    private List<String> groups = new ArrayList<>();
    private int shardCount = 20;
    private int coresToUse = 4;
    private int gbOfMemory = 4;
    private boolean printToStdOut = true;
    private PodLogLevel logLevel = PodLogLevel.INFO;
    private String sidecarImage;
    private List<String> additionalArgs = new ArrayList<>();
    private List<String> taints = new ArrayList<>();

    public DistributeTestsBy getDistribution() {
        return distribution;
    }

    public List<String> getGroups() {
        return groups;
    }

    public int getShardCount() {
        return shardCount;
    }

    public int getCoresToUse() {
        return coresToUse;
    }

    public int getGbOfMemory() {
        return gbOfMemory;
    }

    public boolean getPrintToStdOut() {
        return printToStdOut;
    }

    public PodLogLevel getLogLevel() {
        return logLevel;
    }

    public String getSidecarImage() {
        return sidecarImage;
    }

    public List<String> getAdditionalArgs() {
        return additionalArgs;
    }

    public List<String> getNodeTaints(){
        return new ArrayList<>(taints);
    }

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

    private void testGroups(List<String> group) {
        groups.addAll(group);
    }

    public void sidecarImage(String sidecarImage) {
        this.sidecarImage = sidecarImage;
    }

    public void additionalArgs(String... additionalArgs) {
        additionalArgs(Arrays.asList(additionalArgs));
    }

    private void additionalArgs(List<String> additionalArgs) {
        this.additionalArgs.addAll(additionalArgs);
    }

    public void nodeTaints(String... additionalArgs) {
        nodeTaints(Arrays.asList(additionalArgs));
    }

    private void nodeTaints(List<String> additionalArgs) {
        this.taints.addAll(additionalArgs);
    }

}
