package net.corda.plugins

import com.typesafe.config.*
import org.gradle.api.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Represents a node that will be installed.
 */
class Node {
    static final String JAR_NAME = 'corda.jar'
    static final String DEFAULT_HOST = 'localhost'

    /**
     * Name of the node.
     */
    public String name
    /**
     * A list of advertised services ID strings.
     */
    protected List<String> advertisedServices = []

    /**
     * If running a distributed notary, a list of node addresses for joining the Raft cluster
     */
    protected List<String> notaryClusterAddresses = []
    /**
     * Set the list of CorDapps to install to the plugins directory. Each cordapp is a fully qualified Maven
     * dependency name, eg: com.example:product-name:0.1
     *
     * @note Your app will be installed by default and does not need to be included here.
     */
    protected List<String> cordapps = []
    /**
     * Set the RPC users for this node. This configuration block allows arbitrary configuration.
     * The recommended current structure is:
     * [[['user': "username_here", 'password': "password_here", 'permissions': ["permissions_here"]]]
     * The above is a list to a map of keys to values using Groovy map and list shorthands.
     *
     * @note Incorrect configurations will not cause a DSL error.
     */
    protected List<Map<String, Object>> rpcUsers = []

    private String dirName
    private Config config = ConfigFactory.empty()
    private File nodeDir
    private Project project

    /**
     * Set the name of the node.
     *
     * @param name The node name.
     */
    void name(String name) {
        this.name = name
        config = config.withValue("myLegalName", ConfigValueFactory.fromAnyRef(name))
    }

    /**
     * Set the directory the node will be installed to relative to the directory specified in Cordform task.
     *
     * @param dirName Subdirectory name for node to be installed to. Must be valid directory name on all OSes.
     */
    void dirName(String dirName) {
        this.dirName = dirName
        config = config.withValue("basedir", ConfigValueFactory.fromAnyRef(dirName))
    }

    /**
     * Set the nearest city to the node.
     *
     * @param nearestCity The name of the nearest city to the node.
     */
    void nearestCity(String nearestCity) {
        config = config.withValue("nearestCity", ConfigValueFactory.fromAnyRef(nearestCity))
    }

    /**
     * Sets whether this node will use HTTPS communication.
     *
     * @param isHttps True if this node uses HTTPS communication.
     */
    void https(Boolean isHttps) {
        config = config.withValue("useHTTPS", ConfigValueFactory.fromAnyRef(isHttps))
    }

    void useTestClock(Boolean useTestClock) {
        config = config.withValue("useTestClock", ConfigValueFactory.fromAnyRef(useTestClock))
    }

    /**
     * Set the artemis port for this node.
     *
     * @param artemisPort The artemis messaging queue port.
     */
    void artemisPort(Integer artemisPort) {
        config = config.withValue("artemisAddress",
                ConfigValueFactory.fromAnyRef("$DEFAULT_HOST:$artemisPort".toString()))
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
     * Set the port which to bind the Copycat (Raft) node to
     */
    void notaryNodePort(Integer notaryPort) {
        config = config.withValue("notaryNodeAddress",
                ConfigValueFactory.fromAnyRef("$DEFAULT_HOST:$notaryPort".toString()))
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

    Node(Project project) {
        this.project = project
    }

    /**
     * Install the nodes to the given base directory.
     *
     * @param baseDir The base directory for this node. All other paths are relative to it + this nodes dir name.
     */
    void build(File baseDir) {
        nodeDir = new File(baseDir, dirName)
        configureRpcUsers()
        installCordaJAR()
        installBuiltPlugin()
        installCordapps()
        installDependencies()
        installConfig()
    }

    /**
     * Get the artemis address for this node.
     *
     * @return This node's artemis address.
     */
    String getArtemisAddress() {
        return config.getString("artemisAddress")
    }

    /**
     * Write the RPC users to the config
     */
    private void configureRpcUsers() {
        config = config.withValue("rpcUsers", ConfigValueFactory.fromIterable(rpcUsers))
    }

    /**
     * Installs the corda fat JAR to the node directory.
     */
    private void installCordaJAR() {
        def cordaJar = verifyAndGetCordaJar()
        project.copy {
            from cordaJar
            into nodeDir
            rename cordaJar.name, JAR_NAME
        }
    }

    /**
     * Installs this project's cordapp to this directory.
     */
    private void installBuiltPlugin() {
        def pluginsDir = new File(nodeDir, "plugins")
        project.copy {
            from project.jar
            into pluginsDir
        }
    }

    /**
     * Installs other cordapps to this node's plugins directory.
     */
    private void installCordapps() {
        def pluginsDir = new File(nodeDir, "plugins")
        def cordapps = getCordappList()
        project.copy {
            from cordapps
            into pluginsDir
        }
    }

    /**
     * Installs other dependencies to this node's dependencies directory.
     */
    private void installDependencies() {
        def cordaJar = verifyAndGetCordaJar()
        def cordappDeps = getCordappList()
        def depsDir = new File(nodeDir, "dependencies")
        def coreDeps = project.zipTree(cordaJar).getFiles().collect { it.getName() }
        def appDeps = project.configurations.runtime.filter {
            it != cordaJar && !cordappDeps.contains(it) && !coreDeps.contains(it.getName())
        }
        project.copy {
            from appDeps
            into depsDir
        }
    }

    /**
     * Installs the configuration file to this node's directory and detokenises it.
     */
    private void installConfig() {
        // Adding required default values
        config = config.withValue('extraAdvertisedServiceIds', ConfigValueFactory.fromAnyRef(advertisedServices.join(',')))
        if (notaryClusterAddresses.size() > 0) {
            config = config.withValue('notaryClusterAddresses', ConfigValueFactory.fromIterable(notaryClusterAddresses))
        }
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
     * Find the corda JAR amongst the dependencies.
     *
     * @return A file representing the Corda JAR.
     */
    private File verifyAndGetCordaJar() {
        def maybeCordaJAR = project.configurations.runtime.filter {
            it.toString().contains("corda-${project.corda_version}.jar")
        }
        if (maybeCordaJAR.size() == 0) {
            throw new RuntimeException("No Corda Capsule JAR found. Have you deployed the Corda project to Maven? Looked for \"corda-${project.corda_version}.jar\"")
        } else {
            def cordaJar = maybeCordaJAR.getSingleFile()
            assert(cordaJar.isFile())
            return cordaJar
        }
    }

    /**
     * Gets a list of cordapps based on what dependent cordapps were specified.
     *
     * @return List of this node's cordapps.
     */
    private Collection<File> getCordappList() {
        return project.configurations.cordapp.files {
            cordapps.contains("${it.group}:${it.name}:${it.version}")
        }
    }
}
