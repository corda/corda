package com.r3corda.plugins

import groovy.text.SimpleTemplateEngine
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.Project

/**
 * Represents a node that will be installed.
 */
class Node {
    static final String JAR_NAME = 'corda.jar'

    /**
     * Name of the node.
     */
    public String name
    private String dirName
    private String nearestCity
    private Boolean isHttps = false
    private List<String> advertisedServices = []
    private Integer artemisPort
    private Integer webPort
    private String networkMapAddress = ""
    protected List<String> cordapps = []

    private File nodeDir
    private def project

    /**
     * Set the name of the node.
     *
     * @param name The node name.
     */
    void name(String name) {
        this.name = name
    }

    /**
     * Set the directory the node will be installed to relative to the directory specified in Cordform task.
     *
     * @param dirName Subdirectory name for node to be installed to. Must be valid directory name on all OSes.
     */
    void dirName(String dirName) {
        this.dirName = dirName
    }

    /**
     * Set the nearest city to the node.
     *
     * @param nearestCity The name of the nearest city to the node.
     */
    void nearestCity(String nearestCity) {
        this.nearestCity = nearestCity
    }

    /**
     * Sets whether this node will use HTTPS communication.
     *
     * @param isHttps True if this node uses HTTPS communication.
     */
    void https(Boolean isHttps) {
        this.isHttps = isHttps
    }

    /**
     * Set the advertised services for this node.
     *
     * @param advertisedServices A list of advertised services ID strings.
     */
    void advertisedServices(List<String> advertisedServices) {
        this.advertisedServices = advertisedServices
    }

    /**
     * Set the artemis port for this node.
     *
     * @param artemisPort The artemis messaging queue port.
     */
    void artemisPort(Integer artemisPort) {
        this.artemisPort = artemisPort
    }

    /**
     * Set the HTTP web server port for this node.
     *
     * @param webPort The web port number for this node.
     */
    void webPort(Integer webPort) {
        this.webPort = webPort
    }

    /**
     * Set the network map address for this node.
     *
     * @warning This should not be directly set unless you know what you are doing. Use the networkMapName in the
     *          Cordform task instead.
     * @param networkMapAddress Network map address.
     */
    void networkMapAddress(String networkMapAddress) {
        this.networkMapAddress = networkMapAddress
    }

    /**
     * Set the list of cordapps to use on this node.
     *
     * @note Your app will be installed by default and does not need to be included here.
     * @param cordapps The list of cordapps to install to the plugins directory.
     */
    void cordapps(List<String> cordapps) {
        this.cordapps = cordapps
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
        return "localhost:" + artemisPort
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
        def pluginsDir = getAndCreateDirectory(nodeDir, "plugins")
        project.copy {
            from project.jar
            into pluginsDir
        }
    }

    /**
     * Installs other cordapps to this node's plugins directory.
     */
    private void installCordapps() {
        def pluginsDir = getAndCreateDirectory(nodeDir, "plugins")
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
        def cordappList = getCordappList()
        def depsDir = getAndCreateDirectory(nodeDir, "dependencies")
        def appDeps = project.configurations.runtime.filter { it != cordaJar && !cordappList.contains(it) }
        project.copy {
            from appDeps
            into depsDir
        }
    }

    /**
     * Installs the configuration file to this node's directory and detokenises it.
     */
    private void installConfig() {
        project.copy {
            from Cordformation.getPluginFile(project, 'com/r3corda/plugins/nodetemplate.conf')
            filter {
                def binding = [
                    "name": name,
                    "dirName": dirName,
                    "nearestCity": nearestCity,
                    "isHttps": isHttps,
                    "advertisedServices": advertisedServices.join(","),
                    "networkMapAddress": networkMapAddress,
                    "artemisPort": artemisPort.toString(),
                    "webPort": webPort.toString()
                ]

                def engine = new SimpleTemplateEngine()
                engine.createTemplate(it).make(binding)
            }
            into nodeDir
            rename 'nodetemplate.conf', 'node.conf'
        }
    }

    /**
     * Find the corda JAR amongst the dependencies.
     *
     * @return A file representing the Corda JAR.
     */
    private File verifyAndGetCordaJar() {
        def maybeCordaJAR = project.configurations.runtime.filter { it.toString().contains("corda-${project.corda_version}.jar")}
        if(maybeCordaJAR.size() == 0) {
            throw new RuntimeException("No Corda Capsule JAR found. Have you deployed the Corda project to Maven?")
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
    private AbstractFileCollection getCordappList() {
        def cordaJar = verifyAndGetCordaJar()
        return project.configurations.runtime.filter {
            def jarName = it.name.split('-').first()
            return (it != cordaJar) && cordapps.contains(jarName)
        }
    }

    /**
     * Create a directory if it doesn't exist and return the file representation of it.
     *
     * @param baseDir The base directory to create the directory at.
     * @param subDirName A valid name of the subdirectory to get and create if not exists.
     * @return A file representing the subdirectory.
     */
    private static File getAndCreateDirectory(File baseDir, String subDirName) {
        File dir = new File(baseDir, subDirName)
        assert(!dir.exists() || dir.isDirectory())
        dir.mkdirs()
        return dir
    }
}
