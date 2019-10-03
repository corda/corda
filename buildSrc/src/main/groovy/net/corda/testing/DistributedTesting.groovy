package net.corda.testing

import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test

/**
 This plugin is responsible for wiring together the various components of test task modification
 */
class DistributedTesting implements Plugin<Project> {

    //<editor-fold desc="statics">
    static def getPropertyAsInt(Project proj, String property, Integer defaultValue) {
        return proj.hasProperty(property) ? Integer.parseInt(proj.property(property).toString()) : defaultValue
    }

    static def getForkCount(Project project) {
        return getPropertyAsInt(project, "dockerForks", 1)
    }

    static def getForkIdx(Project project) {
        return getPropertyAsInt(project, "dockerFork", 0)
    }

    static def getDockerTag() {
        return System.getProperty("docker.tag")
    }
    //</editor-fold>

    // All the tests marked '@Test' in all the projects are going to be stored in here
    List<String> allTestsInAllProjects = new ArrayList<>()

    @Override
    void apply(Project project) {
        if (System.getProperty("kubenetize") != null) {
            project.plugins.apply(ImageBuilding)
            ImageBuilding imagePlugin = project.plugins.getPlugin(ImageBuilding)
            DockerPushImage imageBuildingTask = imagePlugin.pushTask
            String providedTag = getDockerTag()

            // This makes all tests depend on the compilation phase of every other test
            // so that we can inspect every jar for their tests, and then allocate tests
            // to a k8s fork.
            def globalDependency = project.tasks.create("GlobalDependency")

            //in each subproject
            //1. add the task to determine all tests within the module
            //2. modify the underlying testing task to use the output of the listing task to include a subset of tests for each fork
            //3. KubesTest will invoke these test tasks in a parallel fashion on a remote k8s cluster
            project.subprojects { Project subproject ->
                subproject.tasks.withType(Test) { Test testTask ->
                    if (!testTask.path.contains("core-deterministic")) {
                        // testTask.dependsOn globalDependency
                        ListTests testListerTask = createTestListingTasks(testTask, subproject, allTestsInAllProjects, globalDependency)
                        // globalDependency.dependsOn testListerTask

                        subproject.logger.info("Added dependency: {} -> {} -> {}", testTask, globalDependency, testListerTask)
                        configureTestTaskForParallelExecution(subproject, testTask, testListerTask)
                        createParallelTestingTask(subproject, testTask, imageBuildingTask, providedTag)
                    } else {
                        subproject.logger.info("Skipping core-deterministic tests, dependencies: {}", testTask.dependsOn.size())
                        testTask.dependsOn.forEach { subproject.logger.info("+  dependency:  {}", it.toString()) }
                    }
                }
            }


            //now we are going to create "super" groupings of these KubesTest tasks, so that it is possible to invoke all submodule tests with a single command
            //group all kubes tests by their underlying target task (test/integrationTest/smokeTest ... etc)
            Map<String, List<KubesTest>> allKubesTestingTasksGroupedByType = project.subprojects
                    .collect { prj -> prj.getAllTasks(false).values() }
                    .flatten()
                    .findAll { task -> task instanceof KubesTest }
                    .groupBy { task -> task.taskToExecuteName }

            //first step is to create a single task which will invoke all the submodule tasks for each grouping
            //ie allParallelTest will invoke [node:test, core:test, client:rpc:test ... etc]
            //ie allIntegrationTest will invoke [node:integrationTest, core:integrationTest, client:rpc:integrationTest ... etc]
            Set<ParallelTestGroup> userGroups = new HashSet<>(project.tasks.withType(ParallelTestGroup))

            Collection<ParallelTestGroup> userDefinedGroups = userGroups.forEach { testGrouping ->
                List<KubesTest> groups = ((ParallelTestGroup) testGrouping).groups
                        .collect { allKubesTestingTasksGroupedByType.get(it) }
                        .flatten()

                String superListOfTasks = groups.collect { it.fullTaskToExecutePath }.join(" ")

                def userDefinedParallelTask = project.rootProject.tasks.create("userDefined" + testGrouping.name.capitalize(), KubesTest) {
                    if (!providedTag) {
                        dependsOn imageBuildingTask
                    }
                    numberOfPods = testGrouping.getShardCount()
                    printOutput = testGrouping.printToStdOut
                    fullTaskToExecutePath = superListOfTasks
                    taskToExecuteName = testGrouping.groups.join("And")
                    memoryGbPerFork = testGrouping.gbOfMemory
                    numberOfCoresPerFork = testGrouping.coresToUse
                    doFirst {
                        dockerTag = providedTag ? ImageBuilding.registryName + ":" + providedTag : (imageBuildingTask.imageName.get() + ":" + imageBuildingTask.tag.get())
                    }
                }
                def reportOnAllTask = project.rootProject.tasks.create("userDefinedReports${testGrouping.name.capitalize()}", KubesReporting) {
                    dependsOn userDefinedParallelTask
                    destinationDir new File(project.rootProject.getBuildDir(), "userDefinedReports${testGrouping.name.capitalize()}")
                    doFirst {
                        destinationDir.deleteDir()
                        shouldPrintOutput = !testGrouping.printToStdOut
                        podResults = userDefinedParallelTask.containerResults
                        reportOn(userDefinedParallelTask.testOutput)
                    }
                }
                userDefinedParallelTask.finalizedBy(reportOnAllTask)
                testGrouping.dependsOn(userDefinedParallelTask)
            }
        }
    }

    private KubesTest createParallelTestingTask(Project projectContainingTask,
                                                Test testTask,
                                                DockerPushImage imageBuildingTask,
                                                String providedTag) {
        def taskName = testTask.getName()
        def capitalizedTaskName = testTask.getName().capitalize()

        KubesTest createdParallelTestTask = projectContainingTask.tasks.create("parallel" + capitalizedTaskName, KubesTest) {
            if (!providedTag) {
                dependsOn imageBuildingTask
            }
            printOutput = true
            fullTaskToExecutePath = testTask.getPath()
            taskToExecuteName = taskName
            doFirst {
                dockerTag = providedTag ? ImageBuilding.registryName + ":" + providedTag : (imageBuildingTask.imageName.get() + ":" + imageBuildingTask.tag.get())
            }
        }
        projectContainingTask.logger.info "Created task: ${createdParallelTestTask.getPath()} to enable testing on kubenetes for task: ${testTask.getPath()}"
        return createdParallelTestTask as KubesTest
    }

    private Test configureTestTaskForParallelExecution(Project subProject, Test testTask, ListTests testListerTask) {
        subProject.logger.info("modifying task: ${testTask.getPath()} to depend on task ${testListerTask.getPath()}")
        def reportsDir = new File(new File(subProject.rootProject.getBuildDir(), "test-reports"), subProject.name + "-" + testTask.name)
        testTask.configure {
            dependsOn testListerTask
            binResultsDir new File(reportsDir, "binary")
            reports.junitXml.destination new File(reportsDir, "xml")
            maxHeapSize = "6g"
            doFirst {
                filter {
                    def fork = getForkIdx(subProject)
                    def forks = getForkCount(subProject)
                    def shuffleSeed = 42
                    subProject.logger.info("requesting tests to include in testing task ${testTask.getPath()} (${fork}, ${forks}, ${shuffleSeed})")
                    List<String> testsForThisProject = testListerTask.getTestsForFork(fork, forks, shuffleSeed)
                    subProject.logger.info "got ${testsForThisProject.size()} tests to include into testing task ${testTask.getPath()}"

                    if (testsForThisProject.isEmpty()) {
                        subProject.logger.info "Disabling test execution for testing task ${testTask.getPath()}"
                        excludeTestsMatching "*"
                    }

                    testsForThisProject.forEach {
                        subProject.logger.info "including: $it for testing task ${testTask.getPath()}"
                        includeTestsMatching it + "*"
                    }
                    failOnNoMatchingTests false
                }
            }
        }

        return testTask
    }

    private ListTests createTestListingTasks(Test testTask,
                                             Project subProject,
                                             List<String> allTestsInAllProjects,
                                             Task globalDependencyTask) {
        def taskName = testTask.getName()
        def capitalizedTaskName = testTask.getName().capitalize()
        def testClassesTask = subProject.getTasks().getByName("${taskName}Classes")
        //determine all the tests which are present in this test task.
        //this list will then be shared between the various worker forks
        def createdListTask = subProject.tasks.create("listTestsFor" + capitalizedTaskName, ListTests, allTestsInAllProjects)
        createdListTask.configure {
            //the convention is that a testing task is backed by a sourceSet with the same name
            dependsOn testClassesTask
            doFirst {
                //we want to set the test scanning classpath to only the output of the sourceSet - this prevents dependencies polluting the list
                scanClassPath = testTask.getTestClassesDirs() ? testTask.getTestClassesDirs() : Collections.emptyList()
            }
        }

        //convenience task to utilize the output of the test listing task to display to local console, useful for debugging missing tests
        def createdPrintTask = subProject.tasks.create("printTestsFor" + capitalizedTaskName) {
            dependsOn createdListTask
            // dependsOn globalDependencyTask  // which will depend on createdListTask

            doLast {
                createdListTask
                        .getTestsForFork(getForkIdx(subProject), getForkCount(subProject), 42)
                        .forEach { logger.lifecycle it }
            }
        }

        subProject.logger.info("created task: {} in project: {} it dependsOn: {}",
                createdListTask.getPath(), subProject, createdListTask.dependsOn)
        subProject.logger.info("created task: {} in project: {} it dependsOn: {}",
                createdPrintTask.getPath(), subProject, createdPrintTask.dependsOn)

        return createdListTask as ListTests
    }

}
