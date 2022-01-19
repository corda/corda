package net.corda.build.docker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.SerializationFeature
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.BuildImageResultCallback
import com.github.dockerjava.api.model.BuildResponseItem
import com.github.dockerjava.api.model.Identifier
import com.github.dockerjava.api.model.PushResponseItem
import com.github.dockerjava.api.model.Repository
import com.github.dockerjava.api.model.ResponseItem
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

class ObjectInputStreamWithCustomClassLoader extends ObjectInputStream {
    private ClassLoader classLoader

    ObjectInputStreamWithCustomClassLoader(InputStream ins, ClassLoader classLoader) {
        super(ins)
        this.classLoader = classLoader
    }

    protected Class<?> resolveClass(ObjectStreamClass desc) {
        return Class.forName(desc.getName(), false, classLoader)
    }
}

class DockerError extends GradleException {
    Integer code
    String msg

    @Override
    String toString() {
        return "Docker error${" " + code ?: ""}: $msg"
    }
}

@CompileStatic
class DockerImage implements Serializable {

    String id

    File baseDir

    Object dockerFile

    String destination

    Set<Identifier> tags = new HashSet<>()

    private void writeObject(ObjectOutputStream oos) {
        oos.writeObject(id)
        oos.writeObject(baseDir)
        oos.writeObject(dockerFile)
        oos.writeObject(destination)
        oos.writeInt(tags.size())
        for(Identifier identifier in tags) {
            oos.writeObject(identifier.repository)
            oos.writeObject(identifier.tag.orElse(null))
        }
    }

    private void readObject(ObjectInputStream ois) {
        id = ois.readObject() as String
        baseDir = ois.readObject() as File
        dockerFile = ois.readObject()
        destination = ois.readObject()
        int len = ois.readInt()
        Set<Identifier> identifiers = new HashSet<>()
        for(int i in 0..<len) {
            Repository repository = ois.readObject() as Repository
            String tag = ois.readObject()
            identifiers.add(new Identifier(repository, tag))
        }
        tags = Collections.unmodifiableSet(identifiers)
    }
}

@PackageScope
class BuildDockerImageCallback extends BuildImageResultCallback {
    private static final ObjectMapper objectMapper = new ObjectMapper()
    private static final ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter()
            .with(SerializationFeature.INDENT_OUTPUT)
    private final Logger logger

    BuildDockerImageCallback(Logger logger) {
        this.logger = logger
    }

    @Override
    void onNext(BuildResponseItem buildResponseItem) {
        super.onNext(buildResponseItem)
        if (buildResponseItem.errorIndicated) {
            ResponseItem.ErrorDetail errorDetail = buildResponseItem.errorDetail
            throw new DockerError(code : errorDetail.code, msg: errorDetail.message)
        } else if(buildResponseItem.stream) {
            String stream = buildResponseItem.stream
            int sz = stream.size()
            String msg
            if (sz > 1) {
                msg = stream.substring(0, sz - 1)
            } else {
                msg = null
            }
            if(msg) {
                logger.info(buildResponseItem.stream.substring(0))
            }
        }
    }
}

class DockerUtils {

    @CompileStatic
    static DockerClient fromConfig(DockerClientConfig cfg) {
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(cfg.dockerHost)
                .sslConfig(cfg.SSLConfig)
                .build()
        return DockerClientBuilder.getInstance(cfg)
                .withDockerHttpClient(httpClient)
                .build()
    }

    @CompileStatic
    static identifier2string(Identifier identifier) {
        return identifier.repository.name + identifier.tag.map { ":" + it}.orElse("")
    }

    @CompileStatic
    static DefaultDockerClientConfig.Builder dockerClientConfigBuilder() {
        def builder = DefaultDockerClientConfig.createDefaultConfigBuilder()
        if(System.getProperty('os.name')?.startsWith('Windows')) {
            builder.withDockerHost("npipe:////./pipe/docker_engine")
        }
        return builder
    }

    @CompileStatic
    static def buildImage(Project project, DockerClient dockerClient, DockerImage img) {
        BuildDockerImageCallback callback = new BuildDockerImageCallback(project.logger)
        File dockerFile
        switch(img.dockerFile) {
            case String:
                def candidate = new File(img.dockerFile as String)
                if(candidate.isAbsolute()) {
                    dockerFile = candidate
                } else {
                    dockerFile = new File(img.baseDir, img.dockerFile as String)
                }
                break
            case File:
                dockerFile = img.dockerFile as File
                break
            case null:
                dockerFile = new File(img.baseDir, "Dockerfile")
                break
            default:
                throw new IllegalArgumentException("Unsupported object type for 'dockerFile': ${img.dockerFile.getClass().name}")
        }
        project.logger.info("Building image from ${img.baseDir.absolutePath} using Dockerfile: ${dockerFile}")
        dockerClient.buildImageCmd(dockerFile)
                .withTags(img.tags.collect { Identifier identifier ->
                    identifier.repository.name + identifier.tag.map { ':' + it}.orElse('')
                }.toSet())
                .exec(callback)
        img.id = callback.awaitImageId()
    }

    @CompileStatic
    static def buildImages(Project project, Iterable<DockerImage> images) {
        DockerClientConfig cfg = dockerClientConfigBuilder().build()
        DockerClient dockerClient = fromConfig(cfg)
        images.each { buildImage(project, dockerClient, it) }
    }

    @CompileStatic
    static def pushImages(Project project, Stream<DockerImage> imageStream) {
        def logger = project.logger
        Map<String, List<DockerImage>> destinationMap = imageStream.collect(
                Collectors.<DockerImage, String>groupingBy{ DockerImage img -> img.destination ?: "" })
        for(def entry in destinationMap.entrySet()) {
            def destination = entry.key
            List<DockerImage> images = entry.value
            def configBuilder = dockerClientConfigBuilder()
            System.getenv('DOCKER_USERNAME')?.with {
                configBuilder.withRegistryUsername(it)
            }
            System.getenv('DOCKER_PASSWORD')?.with {
                configBuilder.withRegistryPassword(it)
            }
            if(destination) {
                configBuilder.withRegistryUrl(destination)
            }
            DockerClientConfig cfg = configBuilder.build()
            DockerClient dockerClient = fromConfig(cfg)
            logger.info("Ready to push to push to ${cfg.registryUrl}")
            images.forEach(new Consumer<DockerImage>() {
                @Override
                void accept(DockerImage img) {
                    pushImage(project, dockerClient, img)
                }
            })
        }
    }

    @CompileStatic
    static def pushImage(Project project, DockerClient dockerClient, DockerImage image) {
        def logger = project.logger
        image.tags.each { identifier ->
            ResultCallback<PushResponseItem> callback = new ResultCallback.Adapter<PushResponseItem>() {
                @Override
                void onNext(PushResponseItem item) {
                    if (item.errorIndicated) {
                        throw new DockerError(msg: item.errorDetail.message)
                    } else if (item.status == 'Preparing') {
                        logger.info("Preparing ${item.id}")
                    } else if (item.status == 'Waiting') {
                        logger.info("Waiting for ${item.id}")
                    } else if (item.status == 'Pushing') {
                        if (item.progressDetail) {
                            ResponseItem.ProgressDetail progressDetail = item.progressDetail
                            logger.debug("Pushing ${item.id}, progress ${progressDetail.current}/${progressDetail.total}")
                        }
                    } else if (item.status == 'Pushed') {
                        logger.info("Pushed ${item.id}")
                    }
                }
            }
            dockerClient.pushImageCmd(identifier).exec(callback)
            callback.awaitCompletion()
            logger.info("Pushed ${identifier2string(identifier)}")
        }
    }
}


