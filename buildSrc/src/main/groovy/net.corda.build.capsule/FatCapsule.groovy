package net.corda.build.capsule

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Jar

/**
 * Builds a "fat" [Capsule](https://github.com/puniverse/capsule) JAR, i.e. an executable JAR which embeds the application and its
 * dependencies, and launches them in a new JVM configured by the capsule manifest attributes.
 *
 * This replaces the third-party `us.kirchmeier.capsule` Gradle plugin, which is not compatible with Gradle 9. Only the subset of its
 * features used by Corda are supported, with the same DSL:
 *
 * <pre>
 * tasks.register('buildCordaJAR', FatCapsule) {
 *     applicationClass 'net.corda.node.Corda'
 *     applicationSource = files(...)
 *     capsuleManifest {
 *         applicationVersion = corda_release_version
 *         jvmArgs = ['-Xmx512m']
 *     }
 * }
 * </pre>
 */
abstract class FatCapsule extends Jar {
    @Internal
    CapsuleManifest capsuleManifest = new CapsuleManifest()

    /** The application and its dependencies, which are embedded in the root of the capsule. */
    @Internal
    Object applicationSource

    /** Any extra dependencies to embed in the capsule. */
    @Internal
    Configuration embedConfiguration

    FatCapsule() {
        archiveClassifier.set('capsule')
        // The Capsule launcher classes
        from({ project.configurations.getByName(CapsulePlugin.CAPSULE_CONFIGURATION).collect { project.zipTree(it) } })
        from({ applicationSource ?: [] })
        from({ embedConfiguration ?: [] })
    }

    @Override
    protected void copy() {
        // The capsule manifest attributes can only be added once the task has been fully configured
        manifest.attributes(capsuleManifest.toAttributes())
        super.copy()
    }

    @Input
    Map<String, String> getCapsuleManifestAttributes() {
        return capsuleManifest.toAttributes()
    }

    FatCapsule applicationClass(String className) {
        capsuleManifest.applicationClass = className
        return this
    }

    FatCapsule capsuleManifest(@DelegatesTo(CapsuleManifest) Closure configureClosure) {
        project.configure(capsuleManifest, configureClosure)
        return this
    }
}

class CapsuleManifest implements Serializable {
    String applicationId
    String applicationVersion
    String applicationClass
    String minJavaVersion
    List<String> jvmArgs = []
    List<String> javaAgents = []
    List<String> caplets = []
    Map<String, String> systemProperties = [:]

    Map<String, String> toAttributes() {
        if (!applicationClass) {
            throw new IllegalStateException("Missing required capsule manifest attribute: Application-Class")
        }
        def attributes = new LinkedHashMap<String, String>()
        put(attributes, 'Application-ID', applicationId)
        put(attributes, 'Application-Version', applicationVersion)
        put(attributes, 'Application-Class', applicationClass)
        put(attributes, 'Min-Java-Version', minJavaVersion)
        put(attributes, 'JVM-Args', jvmArgs.join(' '))
        put(attributes, 'System-Properties', systemProperties.collect { "$it.key=$it.value" }.join(' '))
        put(attributes, 'Java-Agents', javaAgents.join(' '))
        put(attributes, 'Caplets', caplets.join(' '))
        attributes['Premain-Class'] = 'Capsule'
        attributes['Main-Class'] = 'Capsule'
        return attributes
    }

    private static void put(Map<String, String> attributes, String name, String value) {
        if (value) {
            attributes[name] = value
        }
    }
}

class CapsuleExtension {
    private final Project project

    CapsuleExtension(Project project) {
        this.project = project
    }

    void version(String version) {
        setVersion(version)
    }

    void setVersion(String version) {
        def capsule = project.configurations.getByName(CapsulePlugin.CAPSULE_CONFIGURATION)
        capsule.dependencies.clear()
        project.dependencies.add(capsule.name, "co.paralleluniverse:capsule:$version")
    }
}

class CapsulePlugin {
    static final String CAPSULE_CONFIGURATION = 'capsule'

    static void apply(Project project) {
        project.pluginManager.apply('java')
        project.extensions.extraProperties.set('FatCapsule', FatCapsule)
        project.configurations.create(CAPSULE_CONFIGURATION) {
            it.canBeConsumed = false
        }
        project.extensions.create('capsule', CapsuleExtension, project)
    }
}
