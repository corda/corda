package com.r3corda.plugins

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.AbstractFileCollection

class Node {
    static final String JAR_NAME = 'corda.jar'

    public String name
    private String dirName
    private String nearestCity
    private Boolean isNotary = false
    private Boolean isHttps = false
    private List<String> advertisedServices = []
    private Integer artemisPort
    private Integer webPort
    private String networkMapAddress = ""
    protected List<String> cordapps = []

    private File nodeDir
    private def project

    void name(String name) {
        this.name = name
    }

    void dirName(String dirName) {
        this.dirName = dirName
    }

    void nearestCity(String nearestCity) {
        this.nearestCity = nearestCity
    }

    void notary(Boolean isNotary) {
        this.isNotary = isNotary
    }

    void https(Boolean isHttps) {
        this.isHttps = isHttps
    }

    void advertisedServices(List<String> advertisedServices) {
        this.advertisedServices = advertisedServices
    }

    void artemisPort(Integer artemisPort) {
        this.artemisPort = artemisPort
    }

    void webPort(Integer webPort) {
        this.webPort = webPort
    }

    void networkMapAddress(String networkMapAddress) {
        this.networkMapAddress = networkMapAddress
    }

    void cordapps(List<String> cordapps) {
        this.cordapps = cordapps
    }

    Node(def project) {
        this.project = project
    }

    void build(File baseDir) {
        nodeDir = new File(baseDir, dirName)
        installCordaJAR()
        installBuiltPlugin()
        installCordapps()
        installDependencies()
        installConfig()
    }

    String getArtemisAddress() {
        return "localhost:" + artemisPort
    }

    private void installCordaJAR() {
        def cordaJar = verifyAndGetCordaJar()
        project.copy {
            from cordaJar
            into nodeDir
            rename cordaJar.name, JAR_NAME
        }
    }

    private void installBuiltPlugin() {
        def pluginsDir = getAndCreateDirectory(nodeDir, "plugins")
        project.copy {
            from project.jar
            into pluginsDir
        }
    }

    private void installCordapps() {
        def pluginsDir = getAndCreateDirectory(nodeDir, "plugins")
        def cordapps = getCordappList()
        project.copy {
            from cordapps
            into pluginsDir
        }
    }

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

    private void installConfig() {
        project.copy {
            from ('./buildSrc/templates/nodetemplate.conf') {
                filter { it
                        .replaceAll('@@name@@', name)
                        .replaceAll('@@dirName@@', dirName)
                        .replaceAll('@@nearestCity@@', nearestCity)
                        .replaceAll('@@isNotary@@', isNotary.toString())
                        .replaceAll('@@isHttps@@', isHttps.toString())
                        .replaceAll('@@advertisedServices@@', advertisedServices.join(","))
                        .replaceAll('@@networkMapAddress@@', networkMapAddress)
                        .replaceAll('@@artemisPort@@', artemisPort.toString())
                        .replaceAll('@@webPort@@', webPort.toString())
                }
            }
            into nodeDir
            rename 'nodetemplate.conf', 'node.conf'
        }
    }

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

    private AbstractFileCollection getCordappList() {
        def cordaJar = verifyAndGetCordaJar()
        return project.configurations.runtime.filter {
            def jarName = it.name.split('-').first()
            return (it != cordaJar) && cordapps.contains(jarName)
        }
    }

    private static File getAndCreateDirectory(File baseDir, String subDirName) {
        File dir = new File(baseDir, subDirName)
        assert(!dir.exists() || dir.isDirectory())
        dir.mkdirs()
        return dir
    }
}
