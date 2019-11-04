//package net.corda.testing;
//
//import io.github.classgraph.ClassGraph;
//import io.github.classgraph.ClassInfo;
//import io.github.classgraph.ClassInfoList;
//import org.gradle.api.DefaultTask;
//import org.gradle.api.file.FileCollection;
//import org.gradle.api.tasks.TaskAction;
//
//import java.math.BigInteger;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.List;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
//interface TestLister {
//    List<String> getAllTestsDiscovered();
//}
//
//public class ListTests extends DefaultTask implements TestLister {
//
//    public static final String DISTRIBUTION_PROPERTY = "distributeBy";
//
//    public FileCollection scanClassPath;
//    private List<String> allTests;
//    private DistributeTestsBy distribution = System.getProperty(DISTRIBUTION_PROPERTY) != null && !System.getProperty(DISTRIBUTION_PROPERTY).isEmpty() ?
//            DistributeTestsBy.valueOf(System.getProperty(DISTRIBUTION_PROPERTY)) : DistributeTestsBy.METHOD;
//
//    public List<String> getTestsForFork(int fork, int forks, Integer seed) {
//        BigInteger gitSha = new BigInteger(getProject().hasProperty("corda_revision") ?
//                getProject().property("corda_revision").toString() : "0", 36);
//        if (fork >= forks) {
//            throw new IllegalArgumentException("requested shard ${fork + 1} for total shards ${forks}");
//        }
//        int seedToUse = seed != null ? (seed + (this.getPath()).hashCode() + gitSha.intValue()) : 0;
//        return new ListShufflerAndAllocator(allTests).getTestsForFork(fork, forks, seedToUse);
//    }
//
//    @Override
//    public List<String> getAllTestsDiscovered() {
//        return new ArrayList<>(allTests);
//    }
//
//    @TaskAction
//    void discoverTests() {
//        Collection<String> results;
//        switch (distribution) {
//            case METHOD:
//                results = new ClassGraph()
//                        .enableClassInfo()
//                        .enableMethodInfo()
//                        .ignoreClassVisibility()
//                        .ignoreMethodVisibility()
//                        .enableAnnotationInfo()
//                        .overrideClasspath(scanClassPath)
//                        .scan()
//                        .getClassesWithMethodAnnotation("org.junit.Test")
//                        .stream()
//                        .map(classInfo -> {
//                            ClassInfoList returnList = ClassInfoList.emptyList();
//                            returnList.add(classInfo);
//                            returnList.addAll(classInfo.getSubclasses());
//                            return returnList;
//                        })
//                        .flatMap(ClassInfoList::stream)
//                        .map(classInfo -> classInfo.getMethodInfo().filter(methodInfo -> methodInfo.hasAnnotation("org.junit.Test"))
//                                .stream().map(methodInfo -> classInfo.getName() + "." + methodInfo.getName()))
//                        .flatMap(Function.identity())
//                        .collect(Collectors.toSet());
//
//                this.allTests = results.stream().sorted().collect(Collectors.toList());
//                break;
//            case CLASS:
//                results = new ClassGraph()
//                        .enableClassInfo()
//                        .enableMethodInfo()
//                        .ignoreClassVisibility()
//                        .ignoreMethodVisibility()
//                        .enableAnnotationInfo()
//                        .overrideClasspath(scanClassPath)
//                        .scan()
//                        .getClassesWithMethodAnnotation("org.junit.Test")
//                        .stream()
//                        .map(classInfo -> {
//                            ClassInfoList returnList = classInfo.getSubclasses();
//                            returnList.add(classInfo);
//                            return returnList;
//                        }).collect(Collectors.toList())
//                        .stream()
//                        .flatMap(ClassInfoList::stream)
//                        .collect(Collectors.toList())
//                        .stream()
//                        .map(ClassInfo::getName)
//                        .collect(Collectors.toSet());
//                this.allTests = results.stream().sorted().collect(Collectors.toList());
//                break;
//        }
//        getProject().getLogger().lifecycle("THESE ARE ALL THE TESTSSS!!!!!!!!: " + allTests.toString());
//    }
//}