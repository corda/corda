package com.stefano.testing

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.LogContainerResultCallback
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.util.stream.Collectors
import java.util.stream.IntStream

class DockerTest extends DefaultTask {

    class ContainerForkResult {
        ContainerForkResult(CreateContainerResponse createContainerResponse,
                            LogContainerResultCallback logContainerResultCallback,
                            Thread outputThread) {

            this.createContainerResponse = createContainerResponse;
            this.logContainerResultCallback = logContainerResultCallback;
            this.outputThread = outputThread
        }

        final CreateContainerResponse createContainerResponse;
        final LogContainerResultCallback logContainerResultCallback;
        final Thread outputThread;
    }

    String dockerTag;
    int forkCount = 1


    @TaskAction
    def start() {

        def dockerClient = DockerClientBuilder.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()).build()

        List<ContainerForkResult> containers = IntStream.range(0, forkCount).mapToObj { i ->
            def createContainerResponse = dockerClient.createContainerCmd(dockerTag)
                    .withCmd("bash", "-c", "cd /tmp/source && ./gradlew -PdockerFork=$i -PdockerForks=$forkCount node:test --info")
                    .withAttachStderr(true)
                    .withAttachStdout(true).exec()

            def input = new PipedInputStream()
            def output = new PipedOutputStream(input)

            def outputThread = new Thread({

                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(input))
                    try {
                        String line
                        while ((line = br.readLine()) != null) {
                            println "Container$i: $line"
                        }
                    } finally {
                        br.close()
                    }
                } catch (Exception e) {
                    e.printStackTrace()
                }
            })
            outputThread.setDaemon(true)
            outputThread.start()

            dockerClient.startContainerCmd(createContainerResponse.id)
                    .exec()

            LogContainerResultCallback completionResult = dockerClient.logContainerCmd(createContainerResponse.id)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTailAll()
                    .exec(new LogContainerResultCallback() {

                        @Override
                        void onNext(Frame item) {
                            super.onNext(item)
                            output.write(item.payload)
                            output.flush()
                        }

                        @Override
                        void onError(Throwable throwable) {
                            super.onError(throwable)
                            output.close()
                        }

                        @Override
                        void onComplete() {
                            super.onComplete()
                            output.close()
                        }

                        @Override
                        void close() throws IOException {
                            super.close()
                            output.close()
                        }
                    })
            return new ContainerForkResult(createContainerResponse, completionResult, outputThread)
        }.collect(Collectors.toList())

        containers.forEach { container ->
            container.logContainerResultCallback.awaitCompletion()
            container.outputThread.join()
        }

    }

}
