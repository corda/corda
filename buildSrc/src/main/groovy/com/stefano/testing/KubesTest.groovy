package com.stefano.testing

import io.fabric8.kubernetes.api.model.LocalObjectReference
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.client.*
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import okhttp3.Response
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors

class KubesTest extends DefaultTask {


    String dockerTag;
    public List<File> results = Collections.emptyList()

    @TaskAction
    public void runTestsOnKubes() {
        String runId = new BigInteger(64, new Random()).toString(36).toLowerCase();

        int k8sTimeout = 50 * 1_000;
        Config config = new ConfigBuilder()
                .withConnectionTimeout(k8sTimeout)
                .withRequestTimeout(k8sTimeout)
                .withRollingTimeout(k8sTimeout)
                .withWebsocketTimeout(k8sTimeout)
                .withWebsocketPingInterval(k8sTimeout)
                .build();

        final KubernetesClient client = new DefaultKubernetesClient(config)

        String namespace = "thisisatest";
//            client.apps().deployments().inNamespace(namespace).list().getItems().forEach(deploymentToDelete -> {
//                client.resource(deploymentToDelete).delete();
//            });
//
//            client.pods().inNamespace(namespace).list().getItems().forEach(podToDelete -> {
//                System.out.println("deleting: " + podToDelete.getMetadata().getName());
//                client.resource(podToDelete).delete();
//            });
//
//            Namespace ns = new NamespaceBuilder().withNewMetadata().withName(namespace).addToLabels("this", "rocks").endMetadata().build();
//            client.namespaces().createOrReplace(ns);
//
//            int numberOfNodes = client.nodes().list().getItems().size();
//
//            List<KubePodResult> createdPods = IntStream.range(0, numberOfNodes).parallel().mapToObj(i -> {
//                String podName = "test" + runId + i;
//                Pod podRequest = buildPod(numberOfNodes, i, podName);
//                System.out.println("created pod: " + podName);
//                Pod createdPod = client.pods().inNamespace(namespace).create(podRequest);
//
//
//                AtomicReference<Throwable> errorHolder = new AtomicReference<>();
//                CountDownLatch waiter = new CountDownLatch(1);
//                startBuildAndLogging(client, namespace, numberOfNodes, i, podName, errorHolder, waiter);
//
//                return new KubePodResult(createdPod, errorHolder, waiter);
//            }).collect(Collectors.toList());
//            System.out.println("Pods created, waiting for exit");
//
//            createdPods.forEach(pod -> {
//                try {
//                    pod.waiter.await();
//                    System.out.println("Successfully terminated log streaming for " + pod.createdPod.getMetadata().getName() + " still waiting for " + createdPods.stream().filter(cp -> cp.waiter.getCount() > 0).map(cp -> cp.createdPod.getMetadata().getName()).collect(Collectors.toSet()));
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            });

        System.out.println("All pods have completed! preparing to gather test results");
        List<Pod> items = new ArrayList<>(client.pods().inNamespace(namespace).list().getItems());
        Collections.shuffle(items);

        List<File> downloadedTestDirs = items.stream().map { pod ->
            return downloadTestXmlFromPod(client, namespace, pod, "/home/stefano/IdeaProjects/corda/node/build");
        }.collect(Collectors.toList());

        this.results = downloadedTestDirs

        System.out.println();

//            client.pods().inNamespace(namespace).list().getItems().forEach(podToDelete -> {
//                System.out.println("deleting: " + podToDelete.getMetadata().getName());
//                client.resource(podToDelete).delete();
//            });


    }

    private static void startBuildAndLogging(KubernetesClient client, String namespace, int numberOfPods, int podIdx, String podName, AtomicReference<Throwable> errorHolder, CountDownLatch waiter) {
        try {
            System.out.println("Waiting for pod " + podName + " to start before executing build");
            client.pods().inNamespace(namespace).withName(podName).waitUntilReady(10, TimeUnit.MINUTES);
            System.out.println("pod " + podName + " has started, executing build");
            Watch eventWatch = client.pods().inNamespace(namespace).withName(podName).watch(new Watcher<Pod>() {
                @Override
                public void eventReceived(Watcher.Action action, Pod resource) {
                    System.out.println("[StatusChange]  pod " + resource.getMetadata().getName() + " " + action.name());
                }

                @Override
                public void onClose(KubernetesClientException cause) {
                }
            });

            ExecWatch execWatch = client.pods().inNamespace(namespace).withName(podName)
                    .redirectingInput()
                    .redirectingOutput()
                    .redirectingError()
                    .redirectingErrorChannel()
                    .usingListener(new ExecListener() {
                        @Override
                        public void onOpen(Response response) {
                            System.out.println("Build started on pod " + podName);
                        }

                        @Override
                        public void onFailure(Throwable t, Response response) {
                            System.out.println("Received error from container, exiting");
                            errorHolder.set(t);
                            waiter.countDown();
                        }

                        @Override
                        public void onClose(int code, String reason) {
                            System.out.println("Received onClose() from container");
                            waiter.countDown();
                        }
                    }).exec(getBuildCommand(numberOfPods, podIdx));

            System.out.println("Pod: " + podName + " has started ");

            Thread loggingThread = new Thread({ ->
                BufferedReader br = new BufferedReader(new InputStreamReader(execWatch.getOutput()))
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(("Container" + podIdx + ":   " + line).trim());
                }
            })

            loggingThread.setDaemon(true);
            loggingThread.start();

        } catch (InterruptedException ignored) {
            //we were interrupted whilst waiting for container
        }
    }

    private static Pod buildPod(int numberOfPods, int podIdx, String podName) {
        return new PodBuilder().withNewMetadata().withName(podName).endMetadata()
                .withNewSpec()
                .addNewVolume()
                .withName("gradlecache")
                .withNewHostPath()
                .withPath("/tmp/gradle")
                .withType("DirectoryOrCreate")
                .endHostPath()
                .endVolume()
                .addNewContainer()
                .withImage("stefanotestingcr.azurecr.io/testing:latest")
                .withCommand("bash")
                .withArgs("-c", "sleep 10000000")
                .withName(podName)
                .withNewResources()
                .addToRequests("cpu", new Quantity("2"))
                .endResources()
                .addNewVolumeMount()
                .withName("gradlecache")
                .withMountPath("/tmp/gradle")
                .endVolumeMount()
                .endContainer()
                .withImagePullSecrets(new LocalObjectReference("regcred"))
                .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

    private static String[] getBuildCommand(int numberOfPods, int podIdx) {
        return ["bash", "-c", "cd /tmp/source && ./gradlew -PdockerFork=" + podIdx + " -PdockerForks=" + numberOfPods + " node:test --info"];
    }

    private static File downloadTestXmlFromPod(KubernetesClient client, String namespace, Pod cp, String pathToUncompressTo) {
        String resultsInContainerPath = "/tmp/source/node/build/test-results";
        String binaryResultsInContainerPath = "/tmp/source/node/build/test-results/test/binary";
        String podName = cp.getMetadata().getName();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("nodeBuild");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("saving to " + podName + " results to: " + tempDir.toAbsolutePath().toFile().getAbsolutePath());
        boolean copiedResult = false;
        try {
            client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .dir(resultsInContainerPath)
                    .copy(tempDir);
            copiedResult = true;
        } catch (Exception ignored) {
        }

        if (copiedResult) {
            return findChildPathInDir(new File(tempDir.toFile().getAbsolutePath()), binaryResultsInContainerPath);
        } else {
            return null;
        }
    }

    private static File findChildPathInDir(File start, String pathToFind) {
        Queue<File> filesToInspect = new LinkedList<>(Collections.singletonList(start));

        while (!filesToInspect.isEmpty()) {
            File fileToInspect = filesToInspect.poll();
            if (fileToInspect.getAbsolutePath().endsWith(pathToFind)) {
                return fileToInspect;
            }
            filesToInspect.addAll(Arrays.stream(fileToInspect.listFiles()).filter({ f -> f.isDirectory() }).collect(Collectors.toList()));
        }

        return null;
    }

}
