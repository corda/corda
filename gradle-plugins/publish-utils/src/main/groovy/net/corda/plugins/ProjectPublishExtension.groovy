package net.corda.plugins

class ProjectPublishExtension {
    private PublishTasks task

    void setPublishTask(PublishTasks task) {
        this.task = task
    }

    /**
     * Use a different name from the current project name for publishing.
     * Set this after all other settings that need to be configured
     */
    void name(String name) {
        task.setPublishName(name)
    }

    /**
     * Get the publishing name for this project.
     */
    String name() {
        return task.getPublishName()
    }

    /**
     * True when we do not want to publish default Java components
     */
    Boolean disableDefaultJar = false

    /**
     * True if publishing a WAR instead of a JAR. Forces disableDefaultJAR to "true" when true
     */
    Boolean publishWar = false
}