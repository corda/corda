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
     * True when we do not want to publish default Java components
     */
    Boolean disableDefaultJar = false
}