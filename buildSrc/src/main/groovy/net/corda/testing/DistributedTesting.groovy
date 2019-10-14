package net.corda.testing

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 This plugin is responsible for wiring together the various components of test task modification
 */
class DistributedTesting implements Plugin<Project> {


    static def getPropertyAsInt(Project proj, String property, Integer defaultValue) {
        return proj.hasProperty(property) ? Integer.parseInt(proj.property(property).toString()) : defaultValue
    }

    @Override
    void apply(Project project) {
        if (System.getProperty("kubenetize") != null) {

            def forks = getPropertyAsInt(project, "dockerForks", 1)

            ensureImagePluginIsApplied(project)
            ImageBuilding imagePlugin = project.plugins.getPlugin(ImageBuilding)
            DockerPushImage imagePushTask = imagePlugin.pushTask
            DockerBuildImage imageBuildTask = imagePlugin.buildTask
            String providedTag = System.getProperty("docker.tag")
            BucketingAllocatorTask globalAllocator = project.tasks.create("bucketingAllocator", BucketingAllocatorTask, forks)

            def requestedTasks = project.gradle.startParameter.taskNames.collect { project.tasks.findByPath(it) }

            //in each subproject
            //1. add the task to determine all tests within the module
            //2. modify the underlying testing task to use the output of the listing task to include a subset of tests for each fork
            //3. KubesTest will invoke these test tasks in a parallel fashion on a remote k8s cluster
            project.subprojects { Project subProject ->
                subProject.tasks.withType(Test) { Test task ->
                    println "Evaluating ${task.getPath()}"
                    if (task in requestedTasks && !task.hasProperty("ignoreForDistribution")) {
                        println "Modifying ${task.getPath()}"
                        ListTests testListerTask = createTestListingTasks(task, subProject)
                        globalAllocator.addSource(testListerTask, task)
                        Test modifiedTestTask = modifyTestTaskForParallelExecution(subProject, task, globalAllocator)
                    } else {
                        println "Skipping modification of ${task.getPath()} as it's not scheduled for execution"
                    }
                    if (!task.hasProperty("ignoreForDistribution")) {
                        KubesTest parallelTestTask = generateParallelTestingTask(subProject, task, imagePushTask, providedTag)
                    }

                }
            }

            //now we are going to create "super" groupings of these KubesTest tasks, so that it is possible to invoke all submodule tests with a single command
            //group all kubes tests by their underlying target task (test/integrationTest/smokeTest ... etc)
            Map<String, List<KubesTest>> allKubesTestingTasksGroupedByType = project.subprojects.collect { prj -> prj.getAllTasks(false).values() }
                    .flatten()
                    .findAll { task -> task instanceof KubesTest }
                    .groupBy { task -> task.taskToExecuteName }

            //first step is to create a single task which will invoke all the submodule tasks for each grouping
            //ie allParallelTest will invoke [node:test, core:test, client:rpc:test ... etc]
            //ie allIntegrationTest will invoke [node:integrationTest, core:integrationTest, client:rpc:integrationTest ... etc]
            Set<ParallelTestGroup> userGroups = new HashSet<>(project.tasks.withType(ParallelTestGroup))

            Collection<ParallelTestGroup> userDefinedGroups = userGroups.forEach { testGrouping ->
                List<KubesTest> groups = ((ParallelTestGroup) testGrouping).groups.collect {
                    allKubesTestingTasksGroupedByType.get(it)
                }.flatten()
                String superListOfTasks = groups.collect { it.fullTaskToExecutePath }.join(" ")

                if (testGrouping in requestedTasks && System.getProperty("preAllocatePods") != null) {
                    //this testGroup is a task on the command line
                    PodAllocator allocator = new PodAllocator()
                    imageBuildTask.doFirst {
                        //here we will pre-request the correct number of pods for this testGroup
                        int numberOfPodsToRequest = testGrouping.getShardCount()
                        int coresPerPod = testGrouping.getCoresToUse()
                        int memoryGBPerPod = testGrouping.getGbOfMemory()
                        allocator.allocatePods(numberOfPodsToRequest, coresPerPod, memoryGBPerPod)
                    }

                    imagePushTask.doLast {
                        //the image has been pushed, we are ready to delete the existing pods and schedule the new ones
                        allocator.tearDownPods()
                    }
                }


                def userDefinedParallelTask = project.rootProject.tasks.create("userDefined" + testGrouping.name.capitalize(), KubesTest) {
                    if (!providedTag) {
                        dependsOn imagePushTask
                    }
                    numberOfPods = testGrouping.getShardCount()
                    printOutput = testGrouping.printToStdOut
                    fullTaskToExecutePath = superListOfTasks
                    taskToExecuteName = testGrouping.groups.join("And")
                    memoryGbPerFork = testGrouping.gbOfMemory
                    numberOfCoresPerFork = testGrouping.coresToUse
                    distribution = testGrouping.distribution
                    doFirst {
                        dockerTag = dockerTag = providedTag ? ImageBuilding.registryName + ":" + providedTag : (imagePushTask.imageName.get() + ":" + imagePushTask.tag.get())
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

    private KubesTest generateParallelTestingTask(Project projectContainingTask, Test task, DockerPushImage imageBuildingTask, String providedTag) {
        def taskName = task.getName()
        def capitalizedTaskName = task.getName().capitalize()

        KubesTest createdParallelTestTask = projectContainingTask.tasks.create("parallel" + capitalizedTaskName, KubesTest) {
            if (!providedTag) {
                dependsOn imageBuildingTask
            }
            printOutput = true
            fullTaskToExecutePath = task.getPath()
            taskToExecuteName = taskName
            doFirst {
                dockerTag = providedTag ? ImageBuilding.registryName + ":" + providedTag : (imageBuildingTask.imageName.get() + ":" + imageBuildingTask.tag.get())
            }
        }
        projectContainingTask.logger.info "Created task: ${createdParallelTestTask.getPath()} to enable testing on kubenetes for task: ${task.getPath()}"
        return createdParallelTestTask as KubesTest
    }

    private Test modifyTestTaskForParallelExecution(Project subProject, Test task, BucketingAllocatorTask globalAllocator) {
        subProject.logger.info("modifying task: ${task.getPath()} to depend on task ${globalAllocator.getPath()}")
        def reportsDir = new File(new File(subProject.rootProject.getBuildDir(), "test-reports"), subProject.name + "-" + task.name)
        task.configure {
            dependsOn globalAllocator
            binResultsDir new File(reportsDir, "binary")
            reports.junitXml.destination new File(reportsDir, "xml")
            maxHeapSize = "6g"
            doFirst {
                filter {
                    def fork = getPropertyAsInt(subProject, "dockerFork", 0)
                    subProject.logger.info("requesting tests to include in testing task ${task.getPath()} (idx: ${fork})")
                    List<String> includes = globalAllocator.getTestIncludesForForkAndTestTask(
                            fork,
                            task)
                    subProject.logger.info "got ${includes.size()} tests to include into testing task ${task.getPath()}"

                    if (includes.size() == 0) {
                        subProject.logger.info "Disabling test execution for testing task ${task.getPath()}"
                        excludeTestsMatching "*"
                    }

                    includes.forEach { include ->
                        subProject.logger.info "including: $include for testing task ${task.getPath()}"
                        includeTestsMatching include
                    }
                    failOnNoMatchingTests false
                }
            }
        }

        return task
    }

    private static void ensureImagePluginIsApplied(Project project) {
        project.plugins.apply(ImageBuilding)
    }

    private ListTests createTestListingTasks(Test task, Project subProject) {
        def taskName = task.getName()
        def capitalizedTaskName = task.getName().capitalize()
        //determine all the tests which are present in this test task.
        //this list will then be shared between the various worker forks
        def createdListTask = subProject.tasks.create("listTestsFor" + capitalizedTaskName, ListTests) {
            //the convention is that a testing task is backed by a sourceSet with the same name
            dependsOn subProject.getTasks().getByName("${taskName}Classes")
            doFirst {
                //we want to set the test scanning classpath to only the output of the sourceSet - this prevents dependencies polluting the list
                scanClassPath = task.getTestClassesDirs() ? task.getTestClassesDirs() : Collections.emptyList()
            }
        }

        //convenience task to utilize the output of the test listing task to display to local console, useful for debugging missing tests
        def createdPrintTask = subProject.tasks.create("printTestsFor" + capitalizedTaskName) {
            dependsOn createdListTask
            doLast {
                createdListTask.getTestsForFork(
                        getPropertyAsInt(subProject, "dockerFork", 0),
                        getPropertyAsInt(subProject, "dockerForks", 1),
                        42).forEach { testName ->
                    println testName
                }
            }
        }

        subProject.logger.info("created task: " + createdListTask.getPath() + " in project: " + subProject + " it dependsOn: " + createdListTask.dependsOn)
        subProject.logger.info("created task: " + createdPrintTask.getPath() + " in project: " + subProject + " it dependsOn: " + createdPrintTask.dependsOn)

        return createdListTask as ListTests
    }

}
