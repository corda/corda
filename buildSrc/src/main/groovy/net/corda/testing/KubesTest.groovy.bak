package net.corda.testing


import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.Status
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.utils.Serialization
import net.corda.testing.retry.Retry
import net.corda.testing.retry.Retry.RetryException
import okhttp3.Response
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.IntStream

class KubesTest extends DefaultTask {

    static final ExecutorService executorService = Executors.newCachedThreadPool()
    /**
     * Name of the k8s Secret object that holds the credentials to access the docker image registry
     */
    static final String REGISTRY_CREDENTIALS_SECRET_NAME = "regcred"

    String dockerTag
    String fullTaskToExecutePath
    String taskToExecuteName
    Boolean printOutput = false
    Integer numberOfCoresPerFork = 4
    Integer memoryGbPerFork = 6
    public volatile List<File> testOutput = Collections.emptyList()
    public volatile List<KubePodResult> containerResults = Collections.emptyList()

    String namespace = "thisisatest"
    int k8sTimeout = 50 * 1_000
    int webSocketTimeout = k8sTimeout * 6
    int numberOfPods = 20
    int timeoutInMinutesForPodToStart = 60

    Distribution distribution = Distribution.METHOD

    @TaskAction
    void runTestsOnKubes() {

        try {
            Class.forName("org.apache.commons.compress.archivers.tar.TarArchiveInputStream")
        } catch (ClassNotFoundException ignored) {
            throw new GradleException("Apache Commons compress has not be loaded, this can happen if running from within intellj - please select \"delegate to gradle\" for build and test actions")
        }

        def buildId = System.getProperty("buildId") ? System.getProperty("buildId") :
                (project.hasProperty("corda_revision") ? project.property("corda_revision").toString() : "0")

        def currentUser = System.getProperty("user.name") ? System.getProperty("user.name") : "UNKNOWN_USER"

        String stableRunId = rnd64Base36(new Random(buildId.hashCode() + currentUser.hashCode() + taskToExecuteName.hashCode()))
        String random = rnd64Base36(new Random())

        final KubernetesClient client = new DefaultKubernetesClient(new ConfigBuilder()
                .withConnectionTimeout(k8sTimeout)
                .withRequestTimeout(k8sTimeout)
                .withRollingTimeout(k8sTimeout)
                .withWebsocketTimeout(webSocketTimeout)
                .withWebsocketPingInterval(webSocketTimeout)
                .build())

        try {
            client.pods().inNamespace(namespace).list().getItems().forEach({ podToDelete ->
                if (podToDelete.getMetadata().name.contains(stableRunId)) {
                    project.logger.lifecycle("deleting: " + podToDelete.getMetadata().getName())
                    client.resource(podToDelete).delete()
                }
            })
        } catch (Exception ignored) {
            //it's possible that a pod is being deleted by the original build, this can lead to racey conditions
        }

        List<Future<KubePodResult>> futures = IntStream.range(0, numberOfPods).mapToObj({ i ->
            String podName = "${taskToExecuteName.toLowerCase()}-$stableRunId-$random-$i"
            submitBuild(client, namespace, numberOfPods, i, podName, printOutput, 3)
        }).collect(Collectors.toList())
        this.testOutput = Collections.synchronizedList(futures.collect { it -> it.get().binaryResults }.flatten())
        this.containerResults = futures.collect { it -> it.get() }
    }

    static def rnd64Base36(Random rnd) {
        return new BigInteger(64, rnd)
                .toString(36)
                .toLowerCase()
    }

    Future<KubePodResult> submitBuild(
            KubernetesClient client,
            String namespace,
            int numberOfPods,
            int podIdx,
            String podName,
            boolean printOutput,
            int numberOfRetries
    ) {
        return executorService.submit(new Callable<KubePodResult>() {
            @Override
            KubePodResult call() throws Exception {
                def pvc = createPvc(client, podName)
                return buildRunPodWithRetriesOrThrow(client, namespace, pvc, numberOfPods, podIdx, podName, printOutput, numberOfRetries)
            }
        })
    }

    def createPvc(KubernetesClient client, String name) {
        def pvc = client.persistentVolumeClaims()
                .inNamespace(namespace)
                .createNew()

                .editOrNewMetadata().withName(name).endMetadata()

                .editOrNewSpec()
                .withAccessModes("ReadWriteOnce")
                .editOrNewResources().addToRequests("storage", new Quantity("100Mi")).endResources()
                .endSpec()

                .done()

        addShutdownHook {
            logger.info("Deleting PVC: $pvc.metadata.name")
            client.persistentVolumeClaims().delete(pvc)
        }
        return pvc
    }

    KubePodResult buildRunPodWithRetriesOrThrow(
            KubernetesClient client,
            String namespace,
            PersistentVolumeClaim pvc,
            int numberOfPods,
            int podIdx,
            String podName,
            boolean printOutput,
            int numberOfRetries
    ) {
        addShutdownHook {
            logger.lifecycle("deleting pod: $podName")
            client.pods().inNamespace(namespace).withName(podName).delete()
        }

        try {
            // pods might die, so we retry
            return Retry.fixed(numberOfRetries).run {
                // remove pod if exists
                def oldPod = client.pods().inNamespace(namespace).withName(podName)
                if (oldPod.get()) {
                    logger.lifecycle("deleting pod: $podName")
                    oldPod.delete()
                    while (oldPod.get()) {
                        logger.info("waiting for pod $podName to be removed")
                        Thread.sleep(1000)
                    }
                }

                // recreate and run
                project.logger.lifecycle("creating pod: $podName")
                Pod createdPod = client.pods().inNamespace(namespace).create(buildPodRequest(podName, pvc))
                project.logger.lifecycle("scheduled pod: $podName")

                attachStatusListenerToPod(client, createdPod)
                waitForPodToStart(client, createdPod)

                def stdOutOs = new PipedOutputStream()
                def stdOutIs = new PipedInputStream(4096)
                ByteArrayOutputStream errChannelStream = new ByteArrayOutputStream()

                CompletableFuture<Integer> waiter = new CompletableFuture<>()
                ExecListener execListener = buildExecListenerForPod(podName, errChannelStream, waiter)
                stdOutIs.connect(stdOutOs)
                client.pods().inNamespace(namespace).withName(podName)
                        .writingOutput(stdOutOs)
                        .writingErrorChannel(errChannelStream)
                        .usingListener(execListener)
                        .exec(getBuildCommand(numberOfPods, podIdx))

                File podOutput = startLogPumping(stdOutIs, podIdx, printOutput)
                int resCode = waiter.join()
                project.logger.lifecycle("build has ended on on pod ${podName} (${podIdx}/${numberOfPods}), gathering results")
                def binaryResults = downloadTestXmlFromPod(client, namespace, createdPod)
                return new KubePodResult(resCode, podOutput, binaryResults)
            }
        } catch (RetryException e) {
            throw new RuntimeException("Failed to build in pod ${podName} (${podIdx}/${numberOfPods}) in $numberOfRetries attempts", e)
        }
    }

    File startLogPumping(stdOutIs, podIdx, boolean printOutput) {
        File outputFile = Files.createTempFile("container", ".log").toFile()
        Thread loggingThread = new Thread({ ->
            BufferedWriter out = null
            BufferedReader br = null
            try {
                out = new BufferedWriter(new FileWriter(outputFile))
                br = new BufferedReader(new InputStreamReader(stdOutIs))
                String line
                while ((line = br.readLine()) != null) {
                    def toWrite = ("${taskToExecuteName}/Container" + podIdx + ":   " + line).trim()
                    if (printOutput) {
                        project.logger.lifecycle(toWrite)
                    }
                    out.println(toWrite)
                }
            } catch (IOException ignored) {
            }
            finally {
                out?.close()
                br?.close()
            }
        })

        loggingThread.setDaemon(true)
        loggingThread.start()
        return outputFile
    }

    ExecListener buildExecListenerForPod(podName, errChannelStream, CompletableFuture<Integer> waitingFuture) {

        new ExecListener() {
            final Long start = System.currentTimeMillis()

            @Override
            void onOpen(Response response) {
                project.logger.lifecycle("Build started on pod $podName")
            }

            @Override
            void onFailure(Throwable t, Response response) {
                project.logger.lifecycle("Received error from rom pod $podName")
                waitingFuture.completeExceptionally(t)
            }

            @Override
            void onClose(int code, String reason) {
                project.logger.lifecycle("Received onClose() from pod ${podName}, build took: ${(System.currentTimeMillis() - start) / 1000} seconds")
                try {
                    def errChannelContents = errChannelStream.toString()
                    Status status = Serialization.unmarshal(errChannelContents, Status.class)
                    waitingFuture.complete(status.details?.causes?.first()?.message?.toInteger() ?: 0)
                } catch (Exception e) {
                    waitingFuture.completeExceptionally(e)
                }
            }
        }
    }

    Watch attachStatusListenerToPod(KubernetesClient client, Pod pod) {
        client.pods().inNamespace(pod.metadata.namespace).withName(pod.metadata.name).watch(new Watcher<Pod>() {
            @Override
            void eventReceived(Watcher.Action action, Pod resource) {
                project.logger.lifecycle("[StatusChange]  pod ${resource.getMetadata().getName()}  ${action.name()} (${resource.status.phase})")
            }

            @Override
            void onClose(KubernetesClientException cause) {
            }
        })
    }

    void waitForPodToStart(KubernetesClient client, Pod pod) {
        project.logger.lifecycle("Waiting for pod " + pod.metadata.name + " to start before executing build")
        client.pods().inNamespace(pod.metadata.namespace).withName(pod.metadata.name).waitUntilReady(timeoutInMinutesForPodToStart, TimeUnit.MINUTES)
        project.logger.lifecycle("pod " + pod.metadata.name + " has started, executing build")
    }

    Pod buildPodRequest(String podName, PersistentVolumeClaim pvc) {
        return new PodBuilder()
                .withNewMetadata().withName(podName).endMetadata()

                .withNewSpec()

                .addNewVolume()
                .withName("gradlecache")
                .withNewHostPath()
                .withType("DirectoryOrCreate")
                .withPath("/tmp/gradle")
                .endHostPath()
                .endVolume()

                .addNewVolume()
                .withName("testruns")
                .withNewPersistentVolumeClaim()
                .withClaimName(pvc.metadata.name)
                .endPersistentVolumeClaim()
                .endVolume()

                .addNewContainer()
                .withImage(dockerTag)
                .withCommand("bash")
                .withArgs("-c", "sleep 3600")
                .addNewEnv()
                .withName("DRIVER_NODE_MEMORY")
                .withValue("1024m")
                .withName("DRIVER_WEB_MEMORY")
                .withValue("1024m")
                .endEnv()
                .withName(podName)
                .withNewResources()
                .addToRequests("cpu", new Quantity("${numberOfCoresPerFork}"))
                .addToRequests("memory", new Quantity("${memoryGbPerFork}Gi"))
                .endResources()
                .addNewVolumeMount().withName("gradlecache").withMountPath("/tmp/gradle").endVolumeMount()
                .addNewVolumeMount().withName("testruns").withMountPath("/test-runs").endVolumeMount()
                .endContainer()

                .addNewImagePullSecret(REGISTRY_CREDENTIALS_SECRET_NAME)
                .withRestartPolicy("Never")

                .endSpec()
                .build()
    }

    String[] getBuildCommand(int numberOfPods, int podIdx) {
        return ["bash", "-c",
                "let x=1 ; while [ \${x} -ne 0 ] ; do echo \"Waiting for DNS\" ; curl services.gradle.org > /dev/null 2>&1 ; x=\$? ; sleep 1 ; done ; " +
                        "cd /tmp/source ; " +
                        "let y=1 ; while [ \${y} -ne 0 ] ; do echo \"Preparing build directory\" ; ./gradlew testClasses integrationTestClasses --parallel 2>&1 ; y=\$? ; sleep 1 ; done ;" +
                        "./gradlew -D${ListTests.DISTRIBUTION_PROPERTY}=${distribution.name()} -Dkubenetize -PdockerFork=" + podIdx + " -PdockerForks=" + numberOfPods + " $fullTaskToExecutePath --info 2>&1 ;" +
                        "let rs=\$? ; sleep 10 ; exit \${rs}"]
    }

    Collection<File> downloadTestXmlFromPod(KubernetesClient client, String namespace, Pod cp) {
        String resultsInContainerPath = "/tmp/source/build/test-reports"
        String binaryResultsFile = "results.bin"
        String podName = cp.getMetadata().getName()
        Path tempDir = new File(new File(project.getBuildDir(), "test-results-xml"), podName).toPath()

        if (!tempDir.toFile().exists()) {
            tempDir.toFile().mkdirs()
        }

        project.logger.lifecycle("Saving " + podName + " results to: " + tempDir.toAbsolutePath().toFile().getAbsolutePath())
        client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .dir(resultsInContainerPath)
                .copy(tempDir)

        return findFolderContainingBinaryResultsFile(new File(tempDir.toFile().getAbsolutePath()), binaryResultsFile)
    }

    List<File> findFolderContainingBinaryResultsFile(File start, String fileNameToFind) {
        Queue<File> filesToInspect = new LinkedList<>(Collections.singletonList(start))
        List<File> folders = new ArrayList<>()
        while (!filesToInspect.isEmpty()) {
            File fileToInspect = filesToInspect.poll()
            if (fileToInspect.getAbsolutePath().endsWith(fileNameToFind)) {
                folders.add(fileToInspect.parentFile)
            }

            if (fileToInspect.isDirectory()) {
                filesToInspect.addAll(Arrays.stream(fileToInspect.listFiles()).collect(Collectors.toList()))
            }
        }
        return folders
    }

}
