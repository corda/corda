import org.gradle.api.*
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec

/**
 * QuasarPlugin creates a "quasar" configuration, adds quasar as a dependency and creates a "quasarScan" task that scans
 * for `@Suspendable`s in the code
 */
class QuasarPlugin implements Plugin<Project> {
    void apply(Project project) {

        project.repositories {
            mavenCentral()
        }

        project.configurations.getByName("default")
        project.configurations.create("quasar")
        project.dependencies.add("quasar", "co.paralleluniverse:quasar-core:${project.rootProject.ext.quasar_version}:jdk8@jar")
        project.dependencies.add("compile", "co.paralleluniverse:quasar-core:${project.rootProject.ext.quasar_version}:jdk8")


        project.tasks.withType(Test) {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }
        project.tasks.withType(JavaExec) {
            jvmArgs "-javaagent:${project.configurations.quasar.singleFile}"
            jvmArgs "-Dco.paralleluniverse.fibers.verifyInstrumentation"
        }

        project.task("quasarScan") {
            inputs.files(project.sourceSets.main.output)
            outputs.files(
                    "$project.sourceSets.main.output.resourcesDir/META-INF/suspendables",
                    "$project.sourceSets.main.output.resourcesDir/META-INF/suspendable-supers"
            )
        } << {

            // These lines tell gradle to run the Quasar suspendables scanner to look for unannotated super methods
            // that have @Suspendable sub implementations.  These tend to cause NPEs and are not caught by the verifier
            // NOTE: need to make sure the output isn't on the classpath or every other run it generates empty results, so
            // we explicitly delete to avoid that happening.  We also need to turn off what seems to be a spurious warning in the IDE
            ant.taskdef(name:'scanSuspendables', classname:'co.paralleluniverse.fibers.instrument.SuspendablesScanner',
                    classpath: "${project.sourceSets.main.output.classesDir}:${project.sourceSets.main.output.resourcesDir}:${project.configurations.runtime.asPath}")
            project.delete "$project.sourceSets.main.output.resourcesDir/META-INF/suspendables", "$project.sourceSets.main.output.resourcesDir/META-INF/suspendable-supers"
            ant.scanSuspendables(
                    auto:false,
                    suspendablesFile: "$project.sourceSets.main.output.resourcesDir/META-INF/suspendables",
                    supersFile: "$project.sourceSets.main.output.resourcesDir/META-INF/suspendable-supers") {
                fileset(dir: project.sourceSets.main.output.classesDir)
            }

        }

        project.jar.dependsOn project.quasarScan
    }
}
