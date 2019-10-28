package net.corda.testing;

import com.bmuschko.gradle.docker.DockerRegistryCredentials;
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer;
import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer;
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer;
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer;
import com.bmuschko.gradle.docker.tasks.container.DockerWaitContainer;
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage;
import com.bmuschko.gradle.docker.tasks.image.DockerCommitImage;
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage;
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage;
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage;
import com.bmuschko.gradle.docker.tasks.image.DockerTagImage;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * this plugin is responsible for setting up all the required docker image building tasks required for producing and pushing an
 * image of the current build output to a remote container registry
 */
public class ImageBuilding implements Plugin<Project> {

    public static final String registryName = "stefanotestingcr.azurecr.io/testing";
    public static final String PROVIDE_TAG_FOR_BUILDING_PROPERTY = "docker.build.tag";
    public static final String PROVIDE_TAG_FOR_RUNNING_PROPERTY = "docker.run.tag";
    public DockerPushImage pushTask;
    public DockerBuildImage buildTask;

    @Override
    public void apply(@NotNull final Project project) {

        final DockerRegistryCredentials registryCredentialsForPush = new DockerRegistryCredentials(project.getObjects());
        registryCredentialsForPush.getUsername().set("stefanotestingcr");
        registryCredentialsForPush.getPassword().set(System.getProperty("docker.push.password", ""));

        final DockerPullImage pullTask = project.getTasks().create("pullBaseImage", DockerPullImage.class, dockerPullImage -> {
            dockerPullImage.doFirst(task -> dockerPullImage.setRegistryCredentials(registryCredentialsForPush));
            dockerPullImage.getRepository().set("stefanotestingcr.azurecr.io/buildbase");
            dockerPullImage.getTag().set("latest");
        });


        final DockerBuildImage buildDockerImageForSource = project.getTasks().create("buildDockerImageForSource", DockerBuildImage.class,
                dockerBuildImage -> {
                    dockerBuildImage.dependsOn(Arrays.asList(project.getRootProject().getTasksByName("clean", true), pullTask));
                    dockerBuildImage.getInputDir().set(new File("."));
                    dockerBuildImage.getDockerFile().set(new File(new File("testing"), "Dockerfile"));
                });

        this.buildTask = buildDockerImageForSource;

        final DockerCreateContainer createBuildContainer = project.getTasks().create("createBuildContainer", DockerCreateContainer.class,
                dockerCreateContainer -> {
                    final File baseWorkingDir = new File(System.getProperty("docker.work.dir") != null &&
                            !System.getProperty("docker.work.dir").isEmpty() ?
                            System.getProperty("docker.work.dir") : System.getProperty("java.io.tmpdir"));
                    final File gradleDir = new File(baseWorkingDir, "gradle");
                    final File mavenDir = new File(baseWorkingDir, "maven");
                    dockerCreateContainer.doFirst(task -> {
                        if (!gradleDir.exists()) {
                            gradleDir.mkdirs();
                        }
                        if (!mavenDir.exists()) {
                            mavenDir.mkdirs();
                        }

                        project.getLogger().info("Will use: " + gradleDir.getAbsolutePath() + " for caching gradle artifacts");
                    });
                    dockerCreateContainer.dependsOn(buildDockerImageForSource);
                    dockerCreateContainer.targetImageId(buildDockerImageForSource.getImageId());
                    final Map<String, String> map = new HashMap<>();
                    map.put(gradleDir.getAbsolutePath(), "/tmp/gradle");
                    map.put(mavenDir.getAbsolutePath(), "/home/root/.m2");
                    dockerCreateContainer.getBinds().set(map);
                });


        final DockerStartContainer startBuildContainer = project.getTasks().create("startBuildContainer", DockerStartContainer.class,
                dockerStartContainer -> {
                    dockerStartContainer.dependsOn(createBuildContainer);
                    dockerStartContainer.targetContainerId(createBuildContainer.getContainerId());
                });

        final DockerLogsContainer logBuildContainer = project.getTasks().create("logBuildContainer", DockerLogsContainer.class,
                dockerLogsContainer -> {
                    dockerLogsContainer.dependsOn(startBuildContainer);
                    dockerLogsContainer.targetContainerId(createBuildContainer.getContainerId());
                    dockerLogsContainer.getFollow().set(true);
                });

        final DockerWaitContainer waitForBuildContainer = project.getTasks().create("waitForBuildContainer", DockerWaitContainer.class,
                dockerWaitContainer -> {
                    dockerWaitContainer.dependsOn(logBuildContainer);
                    dockerWaitContainer.targetContainerId(createBuildContainer.getContainerId());
                    dockerWaitContainer.doLast(task -> {
                        if (dockerWaitContainer.getExitCode() != 0) {
                            throw new GradleException("Failed to build docker image, aborting build");
                        }
                    });
                });

        final DockerCommitImage commitBuildImageResult = project.getTasks().create("commitBuildImageResult", DockerCommitImage.class,
                dockerCommitImage -> {
                    dockerCommitImage.dependsOn(waitForBuildContainer);
                    dockerCommitImage.targetContainerId(createBuildContainer.getContainerId());
                });


        final DockerTagImage tagBuildImageResult = project.getTasks().create("tagBuildImageResult", DockerTagImage.class, dockerTagImage -> {
            dockerTagImage.dependsOn(commitBuildImageResult);
            dockerTagImage.getImageId().set(commitBuildImageResult.getImageId());
            dockerTagImage.getTag().set(System.getProperty(PROVIDE_TAG_FOR_BUILDING_PROPERTY, UUID.randomUUID().toString().toLowerCase().substring(0, 12)));
            dockerTagImage.getRepository().set(registryName);
        });


        final DockerPushImage pushBuildImage = project.getTasks().create("pushBuildImage", DockerPushImage.class, dockerPushImage -> {
            dockerPushImage.dependsOn(tagBuildImageResult);
            dockerPushImage.doFirst(task -> dockerPushImage.setRegistryCredentials(registryCredentialsForPush));
            dockerPushImage.getImageName().set(registryName);
            dockerPushImage.getTag().set(tagBuildImageResult.getTag());
        });

        this.pushTask = pushBuildImage;


        final DockerRemoveContainer deleteContainer = project.getTasks().create("deleteBuildContainer", DockerRemoveContainer.class,
                dockerRemoveContainer -> {
                    dockerRemoveContainer.dependsOn(pushBuildImage);
                    dockerRemoveContainer.targetContainerId(createBuildContainer.getContainerId());
                });


        final DockerRemoveImage deleteTaggedImage = project.getTasks().create("deleteTaggedImage", DockerRemoveImage.class,
                dockerRemoveImage -> {
                    dockerRemoveImage.dependsOn(pushBuildImage);
                    dockerRemoveImage.getForce().set(true);
                    dockerRemoveImage.targetImageId(commitBuildImageResult.getImageId());
                });

        final DockerRemoveImage deleteBuildImage = project.getTasks().create("deleteBuildImage", DockerRemoveImage.class,
                dockerRemoveImage -> {
                    dockerRemoveImage.dependsOn(deleteContainer, deleteTaggedImage);
                    dockerRemoveImage.getForce().set(true);
                    dockerRemoveImage.targetImageId(buildDockerImageForSource.getImageId());
                });

        if (System.getProperty("docker.keep.image") == null) {
            pushBuildImage.finalizedBy(deleteContainer, deleteBuildImage, deleteTaggedImage);
        }
    }
}