package net.corda.testing;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PodAllocator {

    private static final int CONNECTION_TIMEOUT = 60_1000;
    private final Logger logger;

    public PodAllocator(Logger logger) {
        this.logger = logger;
    }

    public PodAllocator() {
        this.logger = LoggerFactory.getLogger(PodAllocator.class);
    }

    public void allocatePods(Integer number,
                             Integer coresPerPod,
                             Integer memoryPerPod,
                             String prefix,
                             List<String> taints) {

        Config config = getConfig();
        KubernetesClient client = new DefaultKubernetesClient(config);

        List<Job> podsToRequest = IntStream.range(0, number).mapToObj(i -> buildJob("pa-" + prefix + i, coresPerPod, memoryPerPod, taints)).collect(Collectors.toList());
        List<Job> createdJobs = podsToRequest.stream().map(requestedJob -> {
            String msg = "PreAllocating " + requestedJob.getMetadata().getName();
            if (logger instanceof org.gradle.api.logging.Logger) {
                ((org.gradle.api.logging.Logger) logger).quiet(msg);
            } else {
                logger.info(msg);
            }
            return client.batch().jobs().inNamespace(KubesTest.NAMESPACE).create(requestedJob);
        }).collect(Collectors.toList());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            KubernetesClient tearDownClient = new DefaultKubernetesClient(getConfig());
            tearDownClient.batch().jobs().delete(createdJobs);
        }));
    }

    private Config getConfig() {
        return new ConfigBuilder()
                .withConnectionTimeout(CONNECTION_TIMEOUT)
                .withRequestTimeout(CONNECTION_TIMEOUT)
                .withRollingTimeout(CONNECTION_TIMEOUT)
                .withWebsocketTimeout(CONNECTION_TIMEOUT)
                .withWebsocketPingInterval(CONNECTION_TIMEOUT)
                .build();
    }

    public void tearDownPods(String prefix) {
        io.fabric8.kubernetes.client.Config config = getConfig();
        KubernetesClient client = new DefaultKubernetesClient(config);
        Stream<Job> jobsToDelete = client.batch().jobs().inNamespace(KubesTest.NAMESPACE).list()
                .getItems()
                .stream()
                .sorted(Comparator.comparing(p -> p.getMetadata().getName()))
                .filter(foundPod -> foundPod.getMetadata().getName().contains(prefix));

        List<CompletableFuture<Job>> deleteFutures = jobsToDelete.map(job -> {
            CompletableFuture<Job> result = new CompletableFuture<>();
            Watch watch = client.batch().jobs().inNamespace(job.getMetadata().getNamespace()).withName(job.getMetadata().getName()).watch(new Watcher<Job>() {
                @Override
                public void eventReceived(Action action, Job resource) {
                    if (action == Action.DELETED) {
                        result.complete(resource);
                        String msg = "Successfully deleted job " + job.getMetadata().getName();
                        logger.info(msg);
                    }
                }

                @Override
                public void onClose(KubernetesClientException cause) {
                    String message = "Failed to delete job " + job.getMetadata().getName();
                    if (logger instanceof org.gradle.api.logging.Logger) {
                        ((org.gradle.api.logging.Logger) logger).error(message);
                    } else {
                        logger.info(message);
                    }
                    result.completeExceptionally(cause);
                }
            });
            client.batch().jobs().delete(job);
            return result;
        }).collect(Collectors.toList());

        try {
            CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0])).get(5, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            //ignore - there's nothing left to do
        }
    }


    Job buildJob(String podName, Integer coresPerPod, Integer memoryPerPod, List<String> taints) {
        return new JobBuilder().withNewMetadata().withName(podName).endMetadata()
                .withNewSpec()
                .withTtlSecondsAfterFinished(10)
                .withNewTemplate()
                .withNewMetadata()
                .withName(podName + "-pod")
                .endMetadata()
                .withNewSpec()
                .withTolerations(taints.stream().map(taint -> new TolerationBuilder().withKey("key").withValue(taint).withOperator("Equal").withEffect("NoSchedule").build()).collect(Collectors.toList()))
                .addNewContainer()
                .withImage("busybox:latest")
                .withCommand("sh")
                .withArgs("-c", "sleep 300")
                .withName(podName)
                .withNewResources()
                .addToRequests("cpu", new Quantity(coresPerPod.toString()))
                .addToRequests("memory", new Quantity(memoryPerPod.toString() + "Gi"))
                .endResources()
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

}
