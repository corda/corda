package net.corda.plugins

import com.typesafe.config.*
import net.corda.cordform.CordformNode
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.gradle.api.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a node that will be installed.
 */
class Node extends CordformNode {
    static final String NODEJAR_NAME = 'corda.jar'
    static final String WEBJAR_NAME = 'corda-webserver.jar'

    /**
     * Set the list of CorDapps to install to the cordapps directory. Each cordapp is a fully qualified Maven
     * dependency name, eg: com.example:product-name:0.1
     *
     * @note Your app will be installed by default and does not need to be included here.
     */
    protected List<String> cordapps = []

    protected File nodeDir
    private Project project

    /**
     * Sets whether this node will use HTTPS communication.
     *
     * @param isHttps True if this node uses HTTPS communication.
     */
    void https(Boolean isHttps) {
        config = config.withValue("useHTTPS", ConfigValueFactory.fromAnyRef(isHttps))
    }

    /**
     * Sets the H2 port for this node
     */
    void h2Port(Integer h2Port) {
        config = config.withValue("h2port", ConfigValueFactory.fromAnyRef(h2Port))
    }

    void useTestClock(Boolean useTestClock) {
        config = config.withValue("useTestClock", ConfigValueFactory.fromAnyRef(useTestClock))
    }

    /**
     * Set the HTTP web server port for this node.
     *
     * @param webPort The web port number for this node.
     */
    void webPort(Integer webPort) {
        config = config.withValue("webAddress",
                ConfigValueFactory.fromAnyRef("$DEFAULT_HOST:$webPort".toString()))
    }

    /**
     * Set the network map address for this node.
     *
     * @warning This should not be directly set unless you know what you are doing. Use the networkMapName in the
     *          Cordform task instead.
     * @param networkMapAddress Network map node address.
     * @param networkMapLegalName Network map node legal name.
     */
    void networkMapAddress(String networkMapAddress, String networkMapLegalName) {
        def networkMapService = new HashMap()
        networkMapService.put("address", networkMapAddress)
        networkMapService.put("legalName", networkMapLegalName)
        config = config.withValue("networkMapService", ConfigValueFactory.fromMap(networkMapService))
    }

    /**
     * Set the SSHD port for this node.
     *
     * @param sshdPort The SSHD port.
     */
    void sshdPort(Integer sshdPort) {
        config = config.withValue("sshdAddress",
                ConfigValueFactory.fromAnyRef("$DEFAULT_HOST:$sshdPort".toString()))
    }

    Node(Project project) {
        this.project = project
    }

    protected void rootDir(Path rootDir) {
        def dirName
        try {
            X500Name x500Name = new X500Name(name)
            dirName = x500Name.getRDNs(BCStyle.O).getAt(0).getFirst().getValue().toString()
        } catch(IllegalArgumentException ignore) {
            // Can't parse as an X500 name, use the full string
            dirName = name
        }
        nodeDir = new File(rootDir.toFile(), dirName.replaceAll("\\s",""))
    }

    protected void build() {
        configureProperties()
        installCordaJar()
        if (config.hasPath("webAddress")) {
            installWebserverJar()
        }
        installBuiltCordapp()
        installCordapps()
        installConfig()
        appendOptionalConfig()
    }

    /**
     * Get the artemis address for this node.
     *
     * @return This node's P2P address.
     */
    String getP2PAddress() {
        return config.getString("p2pAddress")
    }

    private void configureProperties() {
        config = config.withValue("rpcUsers", ConfigValueFactory.fromIterable(rpcUsers))
        if (notary) {
            config = config.withValue("notary", ConfigValueFactory.fromMap(notary))
        }
        if (extraConfig) {
            config = config.withFallback(ConfigFactory.parseMap(extraConfig))
        }
    }

    /**
     * Installs the corda fat JAR to the node directory.
     */
    private void installCordaJar() {
        def cordaJar = verifyAndGetCordaJar()
        project.copy {
            from cordaJar
            into nodeDir
            rename cordaJar.name, NODEJAR_NAME
            fileMode 0755
        }
    }

    /**
     * Installs the corda webserver JAR to the node directory
     */
    private void installWebserverJar() {
        def webJar = verifyAndGetWebserverJar()
        project.copy {
            from webJar
            into nodeDir
            rename webJar.name, WEBJAR_NAME
        }
    }

    /**
     * Installs this project's cordapp to this directory.
     */
    private void installBuiltCordapp() {
        def cordappsDir = new File(nodeDir, "cordapps")
        project.copy {
            from project.jar
            into cordappsDir
        }
    }

    /**
     * Installs other cordapps to this node's cordapps directory.
     */
    private void installCordapps() {
        def cordappsDir = new File(nodeDir, "cordapps")
        def cordapps = getCordappList()
        project.copy {
            from cordapps
            into cordappsDir
        }
    }

    /**
     * Installs the configuration file to this node's directory and detokenises it.
     */
    private void installConfig() {
        def configFileText = config.root().render(new ConfigRenderOptions(false, false, true, false)).split("\n").toList()

        // Need to write a temporary file first to use the project.copy, which resolves directories correctly.
        def tmpDir = new File(project.buildDir, "tmp")
        def tmpConfFile = new File(tmpDir, 'node.conf')
        Files.write(tmpConfFile.toPath(), configFileText, StandardCharsets.UTF_8)

        project.copy {
            from tmpConfFile
            into nodeDir
        }
    }

    /**
     * Appends installed config file with properties from an optional file.
     */
    private void appendOptionalConfig() {
        final configFileProperty = "configFile"
        File optionalConfig
        if (project.findProperty(configFileProperty)) { //provided by -PconfigFile command line property when running Gradle task
            optionalConfig = new File(project.findProperty(configFileProperty))
        } else if (config.hasPath(configFileProperty)) {
            optionalConfig = new File(config.getString(configFileProperty))
        }
        if (optionalConfig) {
            if (!optionalConfig.exists()) {
               println "$configFileProperty '$optionalConfig' not found"
            } else {
                def confFile = new File(project.buildDir.getPath() + "/../" + nodeDir, 'node.conf')
                optionalConfig.withInputStream {
                    input -> confFile << input
                }
            }
        }
    }

    /**
     * Find the corda JAR amongst the dependencies.
     *
     * @return A file representing the Corda JAR.
     */
    private File verifyAndGetCordaJar() {
        def maybeCordaJAR = project.configurations.runtime.filter {
            it.toString().contains("corda-${project.corda_release_version}.jar") || it.toString().contains("corda-enterprise-${project.corda_release_version}.jar")
        }
        if (maybeCordaJAR.size() == 0) {
            throw new RuntimeException("No Corda Capsule JAR found. Have you deployed the Corda project to Maven? Looked for \"corda-${project.corda_release_version}.jar\"")
        } else {
            def cordaJar = maybeCordaJAR.getSingleFile()
            assert(cordaJar.isFile())
            return cordaJar
        }
    }

    /**
     * Find the corda JAR amongst the dependencies
     *
     * @return A file representing the Corda webserver JAR
     */
    private File verifyAndGetWebserverJar() {
        def maybeJar = project.configurations.runtime.filter {
            it.toString().contains("corda-webserver-${project.corda_release_version}.jar")
        }
        if (maybeJar.size() == 0) {
            throw new RuntimeException("No Corda Webserver JAR found. Have you deployed the Corda project to Maven? Looked for \"corda-webserver-${project.corda_release_version}.jar\"")
        } else {
            def jar = maybeJar.getSingleFile()
            assert(jar.isFile())
            return jar
        }
    }

    /**
     * Gets a list of cordapps based on what dependent cordapps were specified.
     *
     * @return List of this node's cordapps.
     */
    private Collection<File> getCordappList() {
        // Cordapps can sometimes contain a GString instance which fails the equality test with the Java string
        List<String> cordapps = this.cordapps.collect { it.toString() }
        return project.configurations.cordapp.files {
            cordapps.contains(it.group + ":" + it.name + ":" + it.version)
        }
    }
}
