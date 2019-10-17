package net.corda.testing;

import com.bmuschko.gradle.docker.DockerRegistryCredentials;
import com.bmuschko.gradle.docker.tasks.container.*;
import com.bmuschko.gradle.docker.tasks.image.*;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

/**
 * this plugin is responsible for setting up all the required docker image building tasks required for producing and pushing an
 * image of the current build output to a remote container registry
 */
public class ImageBuilding implements Plugin<Project> {

    public static final String registryName = "stefanotestingcr.azurecr.io/testing";
    public DockerPushImage pushTask;

    @Override
    public void apply(Project project) {

        DockerRegistryCredentials registryCredentialsForPush = new DockerRegistryCredentials(project.getObjects());
        registryCredentialsForPush.getUsername().set("stefanotestingcr");
        String password = System.getProperty("docker.push.password").isEmpty() ? System.getProperty("docker.push.password") : "";
        registryCredentialsForPush.getPassword().set(password);

        DockerPullImage pullTask = project.getTasks().create("pullBaseImage", DockerPullImage.class, dockerPullImage -> {
            dockerPullImage.getRepository().set("stefanotestingcr.azurecr.io/buildbase");
            dockerPullImage.getTag().set("latest");
            dockerPullImage.doFirst(task -> dockerPullImage.setRegistryCredentials(registryCredentialsForPush));
        });


        DockerBuildImage buildDockerImageForSource = project.getTasks().create("buildDockerImageForSource", DockerBuildImage.class,
                dockerBuildImage -> {
                    dockerBuildImage.dependsOn(Arrays.asList(project.getRootProject().getTasksByName("clean", true), pullTask));
                    dockerBuildImage.getInputDir().set(new File("."));
                    dockerBuildImage.getDockerFile().set(new File(new File("testing"), "Dockerfile"));
                });

        DockerCreateContainer createBuildContainer = project.getTasks().create("createBuildContainer", DockerCreateContainer.class,
                dockerCreateContainer -> {
                    File baseWorkingDir = new File(System.getProperty("docker.work.dir").isEmpty() ?
                            System.getProperty("docker.work.dir") : System.getProperty("java.io.tmpdir"));
                    File gradleDir = new File(baseWorkingDir, "gradle");
                    File mavenDir = new File(baseWorkingDir, "maven");
                    dockerCreateContainer.doFirst(task -> {
                        if (!gradleDir.exists()) {
                            gradleDir.mkdirs();
                        }
                        if (!mavenDir.exists()) {
                            mavenDir.mkdirs();
                        }

                        project.getLogger().info("Will use: ${gradleDir.absolutePath} for caching gradle artifacts");
                    });
                    dockerCreateContainer.dependsOn(buildDockerImageForSource);
                    dockerCreateContainer.targetImageId(buildDockerImageForSource.getImageId());
                    HashMap<String, String> map = new HashMap<>();
                    map.put(gradleDir.getAbsolutePath(), "/tmp/gradle");
                    map.put(mavenDir.getAbsolutePath(), "/home/root/.m2");
                    dockerCreateContainer.getBinds().set(map);
                });


        DockerStartContainer startBuildContainer = project.getTasks().create("startBuildContainer", DockerStartContainer.class,
                dockerStartContainer -> {
                    dockerStartContainer.dependsOn(createBuildContainer);
                    dockerStartContainer.targetContainerId(createBuildContainer.getContainerId());
                });

        DockerLogsContainer logBuildContainer = project.getTasks().create("logBuildContainer", DockerLogsContainer.class,
                dockerLogsContainer -> {
                    dockerLogsContainer.dependsOn(startBuildContainer);
                    dockerLogsContainer.targetContainerId(createBuildContainer.getContainerId());
                    dockerLogsContainer.getFollow().set(true);
                });

        DockerWaitContainer waitForBuildContainer = project.getTasks().create("waitForBuildContainer", DockerWaitContainer.class,
                dockerWaitContainer -> {
                    dockerWaitContainer.dependsOn(logBuildContainer);
                    dockerWaitContainer.targetContainerId(createBuildContainer.getContainerId());
                    dockerWaitContainer.doLast(task -> {
                        if (dockerWaitContainer.getExitCode() != 0) {
                            throw new GradleException("Failed to build docker image, aborting build");
                        }
                    });
                });

        DockerCommitImage commitBuildImageResult = project.getTasks().create("commitBuildImageResult", DockerCommitImage.class,
                dockerCommitImage -> {
                    dockerCommitImage.dependsOn(waitForBuildContainer);
                    dockerCommitImage.targetContainerId(createBuildContainer.getContainerId());
                });


        DockerTagImage tagBuildImageResult = project.getTasks().create("tagBuildImageResult", DockerTagImage.class, dockerTagImage -> {
            dockerTagImage.dependsOn(commitBuildImageResult);
            dockerTagImage.getImageId().set(commitBuildImageResult.getImageId());
            dockerTagImage.getTag().set(System.getProperty("docker.provided.tag").isEmpty() ? System.getProperty("docker.provided.tag") :
                    "${UUID.randomUUID().toString().toLowerCase().subSequence(0, 12)}");
            dockerTagImage.getRepository().set(registryName);
        });


        DockerPushImage pushBuildImage = project.getTasks().create("pushBuildImage", DockerPushImage.class, dockerPushImage -> {
            dockerPushImage.dependsOn(tagBuildImageResult);
            dockerPushImage.doFirst(task -> {
                dockerPushImage.setRegistryCredentials(registryCredentialsForPush);
            });
            dockerPushImage.getImageName().set(registryName);
            dockerPushImage.getTag().set(tagBuildImageResult.getTag());
        });

        this.pushTask = pushBuildImage;


        DockerRemoveContainer deleteContainer = project.getTasks().create("deleteBuildContainer", DockerRemoveContainer.class,
                dockerRemoveContainer -> {
                    dockerRemoveContainer.dependsOn(pushBuildImage);
                    dockerRemoveContainer.targetContainerId(createBuildContainer.getContainerId());
                });


        DockerRemoveImage deleteTaggedImage = project.getTasks().create("deleteTaggedImage", DockerRemoveImage.class, dockerRemoveImage -> {
            dockerRemoveImage.dependsOn(pushBuildImage);
            dockerRemoveImage.getForce().set(true);
            dockerRemoveImage.targetImageId(commitBuildImageResult.getImageId());
        });

        DockerRemoveImage deleteBuildImage = project.getTasks().create("deleteBuildImage", DockerRemoveImage.class, dockerRemoveImage -> {
            dockerRemoveImage.dependsOn(deleteContainer, deleteTaggedImage);
            dockerRemoveImage.getForce().set(true);
            dockerRemoveImage.targetImageId(buildDockerImageForSource.getImageId());
        });

        if (System.getProperty("docker.keep.image") == null) {
            pushBuildImage.finalizedBy(deleteContainer, deleteBuildImage, deleteTaggedImage);
        }
    }
}