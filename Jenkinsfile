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

pipeline {
    agent { label 'standard' }

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
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
    }

    stages {
        stage('Compile') {
            steps {
                sh script: [
                        './gradlew',
                        COMMON_GRADLE_PARAMS,
                        'clean',
                        'jar'
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
                        label 'standard'
                    }
                    options {
                        skipDefaultCheckout true
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: '**/*.log', fingerprint: false
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true
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
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'jar'
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
                            archiveArtifacts artifacts: '**/*.log', fingerprint: false
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true
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
            }
        }
    }

    post {
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}
