#!groovy
/**
 * Jenkins pipeline to build Corda Opensource Pull Requests.
 */

@Library('corda-shared-build-pipeline-steps')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

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
].join(' ')

/**
 * The name of subfolders to run tests previously on Another Agent and Same Agent
 */
String sameAgentFolder = 'sameAgent'
String anotherAgentFolder = 'anotherAgent'

pipeline {
    agent {
        dockerfile {
            label 'standard'
            additionalBuildArgs '--build-arg USER="${USER}"' // DON'T change quotation - USER variable is substituted by SHELL!!!!
            filename "${sameAgentFolder}/Dockerfile"
        }
//         docker {
//                 registryUrl 'https://engineering-docker.software.r3.com/'
//                 registryCredentialsId 'artifactory-credentials'
//                 image "build-zulu-openjdk:17"
//                 label "standard"
//                 // Used to mount storage from the host as a volume to persist the cache between builds
//                 args '-v /tmp:/host_tmp'
//                 alwaysPull true
//         }
    }

    /*
     * List options in alphabetical order
     */
    options {
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
        parallelsAlwaysFailFast()
        checkoutToSubdirectory "${sameAgentFolder}"
        timeout(time: 6, unit: 'HOURS')
        timestamps()
    }

    /*
     * List environment variables in alphabetical order
     */
    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
//         GRADLE_USER_HOME = "/host_tmp/gradle"
    }

    stages {
        stage('Compile') {
            steps {
                dir(sameAgentFolder) {
                    authenticateGradleWrapper()
                    sh script: [
                            './gradlew',
                            COMMON_GRADLE_PARAMS,
                            'clean',
                            'jar'
                    ].join(' ')
                }
            }
        }

        stage('Stash') {
            steps {
                sh "rm -rf ${anotherAgentFolder} && mkdir -p ${anotherAgentFolder} &&  cd ${sameAgentFolder} && cp -aR . ../${anotherAgentFolder}"
            }
        }

//         stages('All Tests') {
//             parallel {
                stage('Another agent') {
                    options {
                        skipDefaultCheckout true
                    }
                    post {
                        always {
                            dir(anotherAgentFolder) {
                                archiveArtifacts artifacts: '**/*.log', fingerprint: false
                                junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true
                            }
                        }
                    }
                    stages {
                        stage('Recompile') {
                            steps {
                                dir(anotherAgentFolder) {
                                    authenticateGradleWrapper()
                                    sh script: [
                                            './gradlew',
                                            COMMON_GRADLE_PARAMS,
                                            'jar'
                                    ].join(' ')
                               }
                            }
                        }
                        stage('Unit Test') {
                            steps {
                                dir(anotherAgentFolder) {
                                    sh script: [
                                            './gradlew',
                                            COMMON_GRADLE_PARAMS,
                                            'test'
                                    ].join(' ')
                                }
                            }
                        }
                    }
                }
                stage('Same agent') {
                    post {
                        always {
                            dir(sameAgentFolder) {
                                archiveArtifacts artifacts: '**/*.log', fingerprint: false
                                junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true
                            }
                        }
                    }
                    stages {
                        stage('Integration Test') {
                            steps {
                                dir(sameAgentFolder) {
                                    sh script: [
                                            './gradlew',
                                            COMMON_GRADLE_PARAMS,
                                            'integrationTest'
                                    ].join(' ')
                                }
                            }
                        }
                    }
                }
//             }
//         }
    }

    post {
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}
