#!groovy
/**
 * Jenkins pipeline to build Corda Opensource Pull Requests.
 */

@Library('corda-shared-build-pipeline-steps@5.3') _
import static com.r3.build.BuildControl.killAllExistingBuildsForJob
import com.r3.build.agents.KubernetesAgent
import com.r3.build.enums.KubernetesCluster
import com.r3.build.enums.BuildEnvironment
import com.r3.build.enums.BuildOperatingSystem

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

KubernetesAgent k8s = new KubernetesAgent(
    BuildEnvironment.AMD64_LINUX_JAVA17_CORDA4,
    KubernetesCluster.JenkinsAgents,
    1
).withDocker(
    1,
    false,
    true
)

/**
 * Common Gradle arguments for all Gradle executions
 */
String COMMON_GRADLE_PARAMS = [
        '--no-daemon',
        '--stacktrace',
        '--info',
        /*
        ** revert default behavour for `ignoreFailures` and
        ** do not ignore test failures in PR builds
        */
        '-Ptests.ignoreFailures=false',
        '-Pcompilation.warningsAsErrors=false',
        '-Ptests.failFast=true',
        '-Ddependx.branch.origin="${GIT_COMMIT}"',    // DON'T change quotation - GIT_COMMIT variable is substituted by SHELL!!!!
        '-Ddependx.branch.target="${CHANGE_TARGET}"', // DON'T change quotation - CHANGE_TARGET variable is substituted by SHELL!!!!
        '--build-cache',
].join(' ')

pipeline {
    agent {
        kubernetes {
            cloud k8s.buildCluster.cloudName
            yaml k8s.JSON
            yamlMergeStrategy merge() // important to keep tolerations from the inherited template
            idleMinutes 15
            podRetention always()
            nodeSelector k8s.nodeSelector
            label k8s.jenkinsLabel
            showRawYaml true
            defaultContainer k8s.defaultContainer.name
        }
    }

    /*
     * List options in alphabetical order
     */
    options {
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
        parallelsAlwaysFailFast()
        timeout(time: 6, unit: 'HOURS')
        timestamps()
    }

    /*
     * List environment variables in alphabetical order
     */
    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        BUILD_CACHE_CREDENTIALS = credentials('gradle-ent-cache-credentials')
        BUILD_CACHE_PASSWORD = "${env.BUILD_CACHE_CREDENTIALS_PSW}"
        BUILD_CACHE_USERNAME = "${env.BUILD_CACHE_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_GRADLE_SCAN_KEY = credentials('gradle-build-scans-key')
        CORDA_USE_CACHE = "corda-remotes"
        GRADLE_USER_HOME = "/host_tmp/gradle"
        JAVA_8_HOME = "/usr/lib/jvm/zulu8"
    }

    stages {
        stage('Compile') {
            steps {
                authenticateGradleWrapper()
                sh script: [
                        './gradlew',
                        COMMON_GRADLE_PARAMS,
                        'clean',
                        'jar',
                        '--parallel'
                ].join(' ')
            }
        }

        stage('Stash') {
            steps {
                stash name: 'compiled', useDefaultExcludes: false
            }
        }

        stage('All Tests') {
            parallel {
                stage('Another agent') {
                    agent {
                        kubernetes {
                            cloud k8s.buildCluster.cloudName
                            yaml k8s.JSON
                            yamlMergeStrategy merge() // important to keep tolerations from the inherited template
                            idleMinutes 15
                            podRetention always()
                            nodeSelector k8s.nodeSelector
                            label k8s.jenkinsLabel
                            showRawYaml true
                            defaultContainer k8s.defaultContainer.name
                        }
                    }
                    options {
                        skipDefaultCheckout true
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true, fingerprint: true
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true,allowEmptyResults: true
                        }
                        cleanup {
                            deleteDir() /* clean up our workspace */
                        }
                    }
                    stages {
                        stage('Unstash') {
                            steps {
                                unstash 'compiled'
                            }
                        }
                        stage('Recompile') {
                            steps {
                                authenticateGradleWrapper()
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'jar',
                                        '--parallel'
                                ].join(' ')
                            }
                        }
                        stage('Unit Test') {
                            steps {
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'test'
                                ].join(' ')
                            }
                        }
                    }
                }
                stage('Same agent') {
                    post {
                        always {
                            archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true, fingerprint: true
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true, allowEmptyResults: true
                        }
                    }
                    stages {
                        stage('Integration Test') {
                            steps {
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'integrationTest'
                                ].join(' ')
                            }
                        }
                    }
                }
                stage('Another agent for Integration Test 1') {
                    agent {
                        kubernetes {
                            cloud k8s.buildCluster.cloudName
                            yaml k8s.JSON
                            yamlMergeStrategy merge() // important to keep tolerations from the inherited template
                            idleMinutes 15
                            podRetention always()
                            nodeSelector k8s.nodeSelector
                            label k8s.jenkinsLabel
                            showRawYaml true
                            defaultContainer k8s.defaultContainer.name
                        }
                    }
                    options {
                        skipDefaultCheckout true
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true, fingerprint: false
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: false,allowEmptyResults: true
                        }
                        cleanup {
                            deleteDir() /* clean up our workspace */
                        }
                    }
                    stages {
                        stage('Unstash') {
                            steps {
                                unstash 'compiled'
                            }
                        }
                        stage('Recompile') {
                            steps {
                                authenticateGradleWrapper()
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'jar',
                                        '--parallel'
                                ].join(' ')
                            }
                        }
                        stage('Integration Test 1') {
                            steps {
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'integrationTest1'
                                ].join(' ')
                            }
                        }
                    }
                }
                stage('Another agent for Integration Test 2') {
                    agent {
                        kubernetes {
                            cloud k8s.buildCluster.cloudName
                            yaml k8s.JSON
                            yamlMergeStrategy merge() // important to keep tolerations from the inherited template
                            idleMinutes 15
                            podRetention always()
                            nodeSelector k8s.nodeSelector
                            label k8s.jenkinsLabel
                            showRawYaml true
                            defaultContainer k8s.defaultContainer.name
                        }
                    }
                    options {
                        skipDefaultCheckout true
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true, fingerprint: false
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: false,allowEmptyResults: true
                        }
                        cleanup {
                            deleteDir() /* clean up our workspace */
                        }
                    }
                    stages {
                        stage('Unstash') {
                            steps {
                                unstash 'compiled'
                            }
                        }
                        stage('Recompile') {
                            steps {
                            authenticateGradleWrapper()
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'jar',
                                        '--parallel'
                                ].join(' ')
                            }
                        }
                        stage('Integration Test 2') {
                            steps {
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'integrationTest2'
                                ].join(' ')
                            }
                        }
                        stage('Smoke Test') {
                            steps {
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'smokeTest'
                                ].join(' ')
                            }
                        }
                    }
                }
                stage('Another agent for Slow Integration Tests') {
                    agent {
                        kubernetes {
                            cloud k8s.buildCluster.cloudName
                            yaml k8s.JSON
                            yamlMergeStrategy merge() // important to keep tolerations from the inherited template
                            idleMinutes 15
                            podRetention always()
                            nodeSelector k8s.nodeSelector
                            label k8s.jenkinsLabel
                            showRawYaml true
                            defaultContainer k8s.defaultContainer.name
                        }
                    }
                    options {
                        skipDefaultCheckout true
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true, fingerprint: false
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: false,allowEmptyResults: true
                        }
                        cleanup {
                            deleteDir() /* clean up our workspace */
                        }
                    }
                    stages {
                        stage('Unstash') {
                            steps {
                                unstash 'compiled'
                            }
                        }
                        stage('Recompile') {
                            steps {
                                authenticateGradleWrapper()
                                sh script: [
                                    './gradlew',
                                    COMMON_GRADLE_PARAMS,
                                    'jar',
                                    '--parallel'
                                ].join(' ')
                            }
                        }
                        stage('Slow Integration Test') {
                            steps {
                                sh script: [
                                    './gradlew',
                                    COMMON_GRADLE_PARAMS,
                                    'slowIntegrationTest'
                                ].join(' ')
                            }
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            findBuildScans()
        }
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}
