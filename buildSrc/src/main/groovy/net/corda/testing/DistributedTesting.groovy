package net.corda.testing

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult

import java.util.stream.Collectors

/**
 This plugin is responsible for wiring together the various components of test task modification
 */
class DistributedTesting implements Plugin<Project> {

    public static final String GRADLE_GROUP = "Distributed Testing";

    static def getPropertyAsInt(Project proj, String property, Integer defaultValue) {
        return proj.hasProperty(property) ? Integer.parseInt(proj.property(property).toString()) : defaultValue
    }

    @Override
    void apply(Project project) {
        if (System.getProperty("kubenetize") != null) {
            Properties.setRootProjectType(project.rootProject.name)

            Integer forks = getPropertyAsInt(project, "dockerForks", 1)

            ensureImagePluginIsApplied(project)
            ImageBuilding imagePlugin = project.plugins.getPlugin(ImageBuilding)
            DockerPushImage imagePushTask = imagePlugin.pushTask
            DockerBuildImage imageBuildTask = imagePlugin.buildTask
            String tagToUseForRunningTests = System.getProperty(ImageBuilding.PROVIDE_TAG_FOR_RUNNING_PROPERTY)
            String tagToUseForBuilding = System.getProperty(ImageBuilding.PROVIDE_TAG_FOR_RUNNING_PROPERTY)
            BucketingAllocatorTask globalAllocator = project.tasks.create("bucketingAllocator", BucketingAllocatorTask, forks)
            globalAllocator.group = GRADLE_GROUP
            globalAllocator.description = "Allocates tests to buckets"


            Set<String> requestedTaskNames = project.gradle.startParameter.taskNames.toSet()
            def requestedTasks = requestedTaskNames.collect { project.tasks.findByPath(it) }

            //in each subproject
            //1. add the task to determine all tests within the module and register this as a source to the global allocator
            //2. modify the underlying testing task to use the output of the global allocator to include a subset of tests for each fork
            //3. KubesTest will invoke these test tasks in a parallel fashion on a remote k8s cluster
            //4. after each completed test write its name to a file to keep track of what finished for restart purposes
            project.subprojects { Project subProject ->
                subProject.tasks.withType(Test) { Test task ->
                    project.logger.info("Evaluating ${task.getPath()}")
                    if (task in requestedTasks && !task.hasProperty("ignoreForDistribution")) {
                        project.logger.info "Modifying ${task.getPath()}"
                        Task testListerTask = createTestListingTasks(task, subProject)
                        globalAllocator.addSource(testListerTask, task)
                        Test modifiedTestTask = modifyTestTaskForParallelExecution(subProject, task, globalAllocator)
                    } else {
                        project.logger.info "Skipping modification of ${task.getPath()} as it's not scheduled for execution"
                    }
                    if (!task.hasProperty("ignoreForDistribution")) {
                        //this is what enables execution of a single test suite - for example node:parallelTest would execute all unit tests in node, node:parallelIntegrationTest would do the same for integration tests
                        KubesTest parallelTestTask = generateParallelTestingTask(subProject, task, imagePushTask, tagToUseForRunningTests)
                    }
                }
            }

            //now we are going to create "super" groupings of the Test tasks, so that it is possible to invoke all submodule tests with a single command
            //group all test Tasks by their underlying target task (test/integrationTest/smokeTest ... etc)
            Map<String, List<Test>> allTestTasksGroupedByType = project.subprojects.collect { prj -> prj.getAllTasks(false).values() }
                    .flatten()
                    .findAll { task -> task instanceof Test }
                    .groupBy { Test task -> task.name }

            //first step is to create a single task which will invoke all the submodule tasks for each grouping
            //ie allParallelTest will invoke [node:test, core:test, client:rpc:test ... etc]
            //ie allIntegrationTest will invoke [node:integrationTest, core:integrationTest, client:rpc:integrationTest ... etc]
            //ie allUnitAndIntegrationTest will invoke [node:integrationTest, node:test, core:integrationTest, core:test, client:rpc:test , client:rpc:integrationTest ... etc]
            Set<ParallelTestGroup> userGroups = new HashSet<>(project.tasks.withType(ParallelTestGroup))

            userGroups.forEach { testGrouping ->

                //for each "group" (ie: test, integrationTest) within the grouping find all the Test tasks which have the same name.
                List<Test> testTasksToRunInGroup = ((ParallelTestGroup) testGrouping).getGroups().collect {
                    allTestTasksGroupedByType.get(it)
                }.flatten()

                //join up these test tasks into a single set of tasks to invoke (node:test, node:integrationTest...)
                String superListOfTasks = testTasksToRunInGroup.collect { it.path }.join(" ")

                //generate a preAllocate / deAllocate task which allows you to "pre-book" a node during the image building phase
                //this prevents time lost to cloud provider node spin up time (assuming image build time > provider spin up time)
                def (Task preAllocateTask, Task deAllocateTask) = generatePreAllocateAndDeAllocateTasksForGrouping(project, testGrouping)

                //modify the image building task to depend on the preAllocate task (if specified on the command line) - this prevents gradle running out of order
                if (preAllocateTask.name in requestedTaskNames) {
                    imageBuildTask.dependsOn preAllocateTask
                }

                def userDefinedParallelTask = project.rootProject.tasks.create("userDefined" + testGrouping.getName().capitalize(), KubesTest) {
                    group = GRADLE_GROUP

                    if (!tagToUseForRunningTests) {
                        dependsOn imagePushTask
                    }

                    if (deAllocateTask.name in requestedTaskNames) {
                        dependsOn deAllocateTask
                    }
                    numberOfPods = testGrouping.getShardCount()
                    printOutput = testGrouping.getPrintToStdOut()
                    fullTaskToExecutePath = superListOfTasks
                    taskToExecuteName = testGrouping.getGroups().join("And")
                    memoryGbPerFork = testGrouping.getGbOfMemory()
                    numberOfCoresPerFork = testGrouping.getCoresToUse()
                    distribution = testGrouping.getDistribution()
                    podLogLevel = testGrouping.getLogLevel()
                    doFirst {
                        dockerTag = tagToUseForRunningTests ? (ImageBuilding.registryName + ":" + tagToUseForRunningTests) : (imagePushTask.imageName.get() + ":" + imagePushTask.tag.get())
                    }
                }
                def reportOnAllTask = project.rootProject.tasks.create("userDefinedReports${testGrouping.getName().capitalize()}", KubesReporting) {
                    group = GRADLE_GROUP
                    dependsOn userDefinedParallelTask
                    destinationDir new File(project.rootProject.getBuildDir(), "userDefinedReports${testGrouping.getName().capitalize()}")
                    doFirst {
                        destinationDir.deleteDir()
                        shouldPrintOutput = !testGrouping.getPrintToStdOut()
                        podResults = userDefinedParallelTask.containerResults
                        reportOn(userDefinedParallelTask.testOutput)
                    }
                }

                // Task to zip up test results, and upload them to somewhere (Artifactory).
                def zipTask = TestDurationArtifacts.createZipTask(project.rootProject, testGrouping.name, userDefinedParallelTask)

                userDefinedParallelTask.finalizedBy(reportOnAllTask)
                zipTask.dependsOn(userDefinedParallelTask)
                testGrouping.dependsOn(zipTask)
            }
        }

        //  Added only so that we can manually run zipTask on the command line as a test.
        TestDurationArtifacts.createZipTask(project.rootProject, "zipTask", null)
                .setDescription("Zip task that can be run locally for testing");
    }

    private List<Task> generatePreAllocateAndDeAllocateTasksForGrouping(Project project, ParallelTestGroup testGrouping) {
        PodAllocator allocator = new PodAllocator(project.getLogger())
        Task preAllocateTask = project.rootProject.tasks.create("preAllocateFor" + testGrouping.getName().capitalize()) {
            group = GRADLE_GROUP
            doFirst {
                String dockerTag = System.getProperty(ImageBuilding.PROVIDE_TAG_FOR_BUILDING_PROPERTY)
                if (dockerTag == null) {
                    throw new GradleException("pre allocation cannot be used without a stable docker tag - please provide one  using -D" + ImageBuilding.PROVIDE_TAG_FOR_BUILDING_PROPERTY)
                }
                int seed = (dockerTag.hashCode() + testGrouping.getName().hashCode())
                String podPrefix = new BigInteger(64, new Random(seed)).toString(36)
                //here we will pre-request the correct number of pods for this testGroup
                int numberOfPodsToRequest = testGrouping.getShardCount()
                int coresPerPod = testGrouping.getCoresToUse()
                int memoryGBPerPod = testGrouping.getGbOfMemory()
                allocator.allocatePods(numberOfPodsToRequest, coresPerPod, memoryGBPerPod, podPrefix)
            }
        }

        Task deAllocateTask = project.rootProject.tasks.create("deAllocateFor" + testGrouping.getName().capitalize()) {
            group = GRADLE_GROUP
            doFirst {
                String dockerTag = System.getProperty(ImageBuilding.PROVIDE_TAG_FOR_RUNNING_PROPERTY)
                if (dockerTag == null) {
                    throw new GradleException("pre allocation cannot be used without a stable docker tag - please provide one using -D" + ImageBuilding.PROVIDE_TAG_FOR_RUNNING_PROPERTY)
                }
                int seed = (dockerTag.hashCode() + testGrouping.getName().hashCode())
                String podPrefix = new BigInteger(64, new Random(seed)).toString(36);
                allocator.tearDownPods(podPrefix)
            }
        }
        return [preAllocateTask, deAllocateTask]
    }

    private KubesTest generateParallelTestingTask(Project projectContainingTask, Test task, DockerPushImage imageBuildingTask, String providedTag) {
        def taskName = task.getName()
        def capitalizedTaskName = task.getName().capitalize()

        KubesTest createdParallelTestTask = projectContainingTask.tasks.create("parallel" + capitalizedTaskName, KubesTest) {
            group = GRADLE_GROUP + " Parallel Test Tasks"
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
        def reportsDir = new File(new File(KubesTest.TEST_RUN_DIR, "test-reports"), subProject.name + "-" + task.name)
        reportsDir.mkdirs()
        File executedTestsFile = new File(KubesTest.TEST_RUN_DIR + "/executedTests.txt")
        task.configure {
            dependsOn globalAllocator
            binResultsDir new File(reportsDir, "binary")
            reports.junitXml.destination new File(reportsDir, "xml")
            maxHeapSize = "10g"

            doFirst {
                executedTestsFile.createNewFile()
                filter {
                    List<String> executedTests = executedTestsFile.readLines()
                    //adding wildcard to each test so they match the ones in the includes list
                    executedTests.replaceAll({ test -> test + "*" })
                    def fork = getPropertyAsInt(subProject, "dockerFork", 0)
                    subProject.logger.info("requesting tests to include in testing task ${task.getPath()} (idx: ${fork})")
                    List<String> includes = globalAllocator.getTestIncludesForForkAndTestTask(
                            fork,
                            task)
                    subProject.logger.info "got ${includes.size()} tests to include into testing task ${task.getPath()}"
                    subProject.logger.info "INCLUDE: ${includes.toString()} "
                    subProject.logger.info "got ${executedTests.size()} tests to exclude from testing task ${task.getPath()}"
                    subProject.logger.debug "EXCLUDE: ${executedTests.toString()} "
                    if (includes.size() == 0) {
                        subProject.logger.info "Disabling test execution for testing task ${task.getPath()}"
                        excludeTestsMatching "*"
                    }

                    List<String> intersection = executedTests.stream()
                            .filter(includes.&contains)
                            .collect(Collectors.toList())
                    subProject.logger.info "got ${intersection.size()} tests in intersection"
                    subProject.logger.info "INTERSECTION: ${intersection.toString()} "
                    includes.removeAll(intersection)

                    intersection.forEach { exclude ->
                        subProject.logger.info "excluding: $exclude for testing task ${task.getPath()}"
                        excludeTestsMatching exclude
                    }
                    includes.forEach { include ->
                        subProject.logger.info "including: $include for testing task ${task.getPath()}"
                        includeTestsMatching include
                    }
                    failOnNoMatchingTests false
                }
            }

            afterTest { desc, result ->
                if (result.getResultType() == TestResult.ResultType.SUCCESS ) {
                    executedTestsFile.withWriterAppend { writer ->
                        writer.writeLine(desc.getClassName() + "." + desc.getName())
                    }
                }
            }
        }

        return task
    }

    private static void ensureImagePluginIsApplied(Project project) {
        project.plugins.apply(ImageBuilding)
    }

    private Task createTestListingTasks(Test task, Project subProject) {
        def taskName = task.getName()
        def capitalizedTaskName = task.getName().capitalize()
        //determine all the tests which are present in this test task.
        //this list will then be shared between the various worker forks
        ListTests createdListTask = subProject.tasks.create("listTestsFor" + capitalizedTaskName, ListTests) {
            group = GRADLE_GROUP
            //the convention is that a testing task is backed by a sourceSet with the same name
            dependsOn subProject.getTasks().getByName("${taskName}Classes")
            doFirst {
                //we want to set the test scanning classpath to only the output of the sourceSet - this prevents dependencies polluting the list
                scanClassPath = task.getTestClassesDirs() ? task.getTestClassesDirs() : Collections.emptyList()
            }
        }

        //convenience task to utilize the output of the test listing task to display to local console, useful for debugging missing tests
        def createdPrintTask = subProject.tasks.create("printTestsFor" + capitalizedTaskName) {
            group = GRADLE_GROUP
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

        return createdListTask
    }

}
